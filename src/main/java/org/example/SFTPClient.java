package org.example;

import com.jcraft.jsch.*;
import java.io.*;
import java.util.*;

public class SFTPClient {

    private Session session; // Сессия для подключения к SFTP-серверу
    private ChannelSftp channelSftp; // Канал для работы с файлами через SFTP
    private String filePath; // Путь к файлу на SFTP-сервере

    // Конструктор для инициализации подключения к SFTP-серверу
    public SFTPClient(String host, int port, String username, String password, String filePath, String knownHostsPath) throws JSchException {
        JSch jsch = new JSch(); // Создаем объект JSch для работы с SFTP

        // Загружаем файл known_hosts по указанному пути
        try (FileInputStream knownHostsStream = new FileInputStream(knownHostsPath)) {
            jsch.setKnownHosts(knownHostsStream);
        } catch (Exception e) {
            throw new JSchException("Ошибка при загрузке known_hosts: " + e.getMessage());
        }

        // Создаем сессию для подключения к SFTP-серверу
        session = jsch.getSession(username, host, port);
        session.setPassword(password); // Устанавливаем пароль
//        session.setConfig("StrictHostKeyChecking", "no"); // Отключаем строгую проверку ключа хоста
        session.connect(); // Подключаемся к серверу
        channelSftp = (ChannelSftp) session.openChannel("sftp"); // Открываем канал SFTP
        channelSftp.connect();
        this.filePath = filePath; // Сохраняем путь к файлу
    }

    // Метод для отключения от SFTP-сервера
    public void disconnect() {
        if (channelSftp != null) channelSftp.disconnect();
        if (session != null) session.disconnect();
    }

    // Метод для загрузки файла (похожего на JSON) с SFTP-сервера и его парсинга
    private Map<String, String> loadJsonFromFile() {
        try {
            InputStream inputStream = channelSftp.get(filePath); // Получаем поток для чтения файла
            BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream)); // Читаем файл
            StringBuilder jsonString = new StringBuilder(); // Строка для хранения содержимого файла
            String line;
            while ((line = reader.readLine()) != null) {
                jsonString.append(line); // Добавляем строку в JSON
            }
            reader.close(); // Закрываем поток
            return parseJson(jsonString.toString()); // Парсим JSON и возвращаем результат
        } catch (SftpException | IOException e) {
            throw new RuntimeException(e);
        }
    }

    // Метод для сохранения файла нужного типа на SFTP-сервер
    private void saveJsonToFile(Map<String, String> data) throws IOException, SftpException {
        String jsonString = formatJson(data); // Форматируем данные в похожие на JSON
        ByteArrayInputStream inputStream = new ByteArrayInputStream(jsonString.getBytes()); // Создаем поток для записи
        channelSftp.put(inputStream, filePath); // Записываем данные на сервер
    }

    // Метод для парсинга строк из файла в Map<String, String>
    private Map<String, String> parseJson(String jsonString) {
        Map<String, String> result = new HashMap<>(); // Результирующая Map
        jsonString = jsonString.trim().replaceFirst("\\{\\s*\"addresses\":\\s*\\[", "").replace("]}", ""); // Убираем лишние символы
        String[] entries = jsonString.split("\\},\\s*\\{"); // Разделяем JSON на отдельные записи
        for (String entry : entries) {
            entry = entry.replace("{", "").replace("}", ""); // Убираем фигурные скобки
            String[] pairs = entry.split(","); // Разделяем записи на пары ключ-значение
            String domain = null, ip = null;
            for (String pair : pairs) {
                String[] keyValue = pair.split(":"); // Разделяем ключ и значение
                if (keyValue.length == 2) {
                    String key = keyValue[0].trim().replace("\"", ""); // Убираем кавычки
                    String value = keyValue[1].trim().replace("\"", ""); // Убираем кавычки
                    if (key.equals("domain")) {
                        domain = value; // Сохраняем домен
                    } else if (key.equals("ip")) {
                        ip = value; // Сохраняем IP-адрес
                    }
                }
            }
            if (domain != null && ip != null) {
                result.put(domain, ip); // Добавляем пару в Map
            }
        }
        return result;
    }

    // Метод для форматирования Map<String, String> в JSON-строку
    private String formatJson(Map<String, String> data) {
        StringBuilder json = new StringBuilder("{\n    \"addresses\": [\n"); // Начало файла
        for (Map.Entry<String, String> entry : data.entrySet()) {
            json.append("        {\n")
                    .append("            \"domain\": \"").append(entry.getKey()).append("\",\n")
                    .append("            \"ip\": \"").append(entry.getValue()).append("\"\n")
                    .append("        },\n"); // Добавляем каждую пару в JSON структуре
        }
        if (!data.isEmpty()) {
            json.deleteCharAt(json.length() - 2); // Удаляем последнюю запятую
        }
        json.append("    ]\n}"); // Завершаем типовой файл
        return json.toString();
    }

    // Метод для вывода списка пар "домен – IP"
    private void printDomainIpPairs(Map<String, String> data) {
        TreeMap<String, String> sortedMap = new TreeMap<>(data); // Сортируем данные по домену
        sortedMap.forEach((domain, ip) -> System.out.println(domain + " - " + ip)); // Выводим пары
    }

    // Метод для получения IP-адреса по доменному имени
    private String getIpByDomain(Map<String, String> data, String domain) {
        return data.getOrDefault(domain, "Домен не найден"); // Возвращаем IP или сообщение об ошибке
    }

    // Метод для получения доменного имени по IP-адресу
    private String getDomainByIp(Map<String, String> data, String ip) {
        for (Map.Entry<String, String> entry : data.entrySet()) {
            if (entry.getValue().equals(ip)) {
                return entry.getKey(); // Возвращаем домен, если IP найден
            }
        }
        return "IP-адрес не найден"; // Возвращаем сообщение об ошибке
    }

    // Метод для проверки уникальности домена
    private boolean isUniqueDomain(Map<String, String> data, String domain) {
        return !data.containsKey(domain); // Проверяем, существует ли домен
    }

    // Метод для проверки уникальности IP-адреса
    private boolean isUniqueIp(Map<String, String> data, String ip) {
        return !data.containsValue(ip); // Проверяем, существует ли IP
    }

    // Метод для проверки корректности IPv4-адреса
    private boolean isValidIpv4(String ip) {
        return ip.matches("^((25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\\.){3}(25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)$")
                && !ip.equals("0.0.0.0") && !ip.matches("^((25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\\.){3}0$"); // Проверка по регулярному выражению
    }

    // Метод для добавления новой пары "домен – IP"
    private String addDomainIpPair(Map<String, String> data, String domain, String ip) {
        if (!isUniqueDomain(data, domain)) return "Домен уже существует"; // Проверка уникальности домена
        if (!isUniqueIp(data, ip)) return "IP-адрес уже существует"; // Проверка уникальности IP
        if (!isValidIpv4(ip)) return "Некорректный IP-адрес"; // Проверка корректности IP
        data.put(domain, ip); // Добавляем пару
        return "Пара добавлена"; // Возвращаем сообщение об успехе
    }

    // Метод для удаления пары "домен – IP"
    private String removeDomainIpPair(Map<String, String> data, String key) {
        if (data.containsKey(key)) {
            data.remove(key); // Удаляем по домену
            return "Пара удалена по домену";
        }
        for (Map.Entry<String, String> entry : data.entrySet()) {
            if (entry.getValue().equals(key)) {
                data.remove(entry.getKey()); // Удаляем по IP
                return "Пара удалена по IP-адресу";
            }
        }
        return "Домен или IP-адрес не найдены"; // Возвращаем сообщение об ошибке
    }

    // Основной метод для запуска приложения
    public static void main(String[] args) {

        String knownHostsPath = System.getenv("KNOWN_HOSTS_PATH"); // Получаем путь к known_hosts из переменной окружения
        if (knownHostsPath == null) {
            System.out.println("Переменная окружения KNOWN_HOSTS_PATH не задана.");
            return;
        }

        Scanner scanner = new Scanner(System.in); // Создаем сканер для ввода данных
        System.out.print("Введите адрес SFTP-сервера: ");
        String host = scanner.nextLine(); // Ввод адреса сервера
        System.out.print("Введите порт SFTP-сервера: ");
        int port = scanner.nextInt(); // Ввод порта
        scanner.nextLine(); // Поглотить оставшийся символ новой строки
        System.out.print("Введите логин: ");
        String username = scanner.nextLine(); // Ввод логина
        System.out.print("Введите пароль: ");
        String password = scanner.nextLine(); // Ввод пароля
        System.out.print("Введите путь к файлу на SFTP-сервере: ");
        String filePath = scanner.nextLine(); // Ввод пути к файлу

        try {
            SFTPClient client = new SFTPClient(host, port, username, password, filePath, knownHostsPath); // Создаем клиента
            boolean running = true;
            while (running) {
                Map<String, String> data = client.loadJsonFromFile(); // Загружаем данные

                System.out.println("\nВыберите действие:");
                System.out.println("1. Получить список пар 'домен – адрес'");
                System.out.println("2. Получить IP-адрес по доменному имени");
                System.out.println("3. Получить доменное имя по IP-адресу");
                System.out.println("4. Добавить новую пару 'домен – адрес'");
                System.out.println("5. Удалить пару 'домен – адрес'");
                System.out.println("6. Завершить работу");
                System.out.print("Ваш выбор: ");
                String choice = scanner.nextLine(); // Ввод выбора пользователя

                switch (choice) {
                    case "1":
                        client.printDomainIpPairs(data); // Вывод списка пар
                        break;
                    case "2":
                        System.out.print("Введите доменное имя: ");
                        String domain = scanner.nextLine();
                        System.out.println(client.getIpByDomain(data, domain)); // Получение IP по домену
                        break;
                    case "3":
                        System.out.print("Введите IP-адрес: ");
                        String ip = scanner.nextLine();
                        System.out.println(client.getDomainByIp(data, ip)); // Получение домена по IP
                        break;
                    case "4":
                        System.out.print("Введите доменное имя: ");
                        String newDomain = scanner.nextLine();
                        System.out.print("Введите IP-адрес: ");
                        String newIp = scanner.nextLine();
                        System.out.println(client.addDomainIpPair(data, newDomain, newIp)); // Добавление новой пары
                        client.saveJsonToFile(data); // Сохранение данных
                        break;
                    case "5":
                        System.out.print("Введите доменное имя или IP-адрес: ");
                        String key = scanner.nextLine();
                        System.out.println(client.removeDomainIpPair(data, key)); // Удаление пары
                        client.saveJsonToFile(data); // Сохранение данных
                        break;
                    case "6":
                        running = false; // Завершение работы
                        break;
                    default:
                        System.out.println("Неверный выбор"); // Обработка неверного выбора
                }
            }
            client.disconnect(); // Отключение от сервера
        } catch (Exception e) {
            e.printStackTrace(); // Обработка исключений
        }
    }
}