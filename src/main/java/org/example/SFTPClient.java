package org.example;

import com.jcraft.jsch.*;
import java.io.*;
import java.util.*;

public class SFTPClient {

    private Session session;
    private ChannelSftp channelSftp;
    private String filePath;

    public SFTPClient(String host, int port, String username, String password, String filePath, String knownHostsPath) throws JSchException {
        JSch jsch = new JSch();

        // Загружаем файл known_hosts по указанному пути
        try (FileInputStream knownHostsStream = new FileInputStream(knownHostsPath)) {
            jsch.setKnownHosts(knownHostsStream);
        } catch (Exception e) {
            throw new JSchException("Ошибка при загрузке known_hosts: " + e.getMessage());
        }

        session = jsch.getSession(username, host, port);
        session.setPassword(password);
//        session.setConfig("StrictHostKeyChecking", "no");
        session.connect();
        channelSftp = (ChannelSftp) session.openChannel("sftp"); //нужен для работы с файлами
        channelSftp.connect();
        this.filePath = filePath;
//        this.filePath = "json.txt";
    }

    public void disconnect() {
        if (channelSftp != null) channelSftp.disconnect();
        if (session != null) session.disconnect();
    }

    private Map<String, String> loadJsonFromFile() {
        try {
            InputStream inputStream = channelSftp.get(filePath);
            BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
            StringBuilder jsonString = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                jsonString.append(line);
            }
            reader.close();
            return parseJson(jsonString.toString());
        } catch (SftpException | IOException e) {
            throw new RuntimeException(e);
        }
    }

    private void saveJsonToFile(Map<String, String> data) throws IOException, SftpException {
        String jsonString = formatJson(data); // Форматируем данные в JSON
        ByteArrayInputStream inputStream = new ByteArrayInputStream(jsonString.getBytes());
        channelSftp.put(inputStream, filePath); // Записываем данные на сервер
    }

    private Map<String, String> parseJson(String jsonString) {
        Map<String, String> result = new HashMap<>();
        jsonString = jsonString.trim().replaceFirst("\\{\\s*\"addresses\":\\s*\\[", "").replace("]}", "");
        String[] entries = jsonString.split("\\},\\s*\\{");
        for (String entry : entries) {
            entry = entry.replace("{", "").replace("}", "");
            String[] pairs = entry.split(",");
            String domain = null, ip = null;
            for (String pair : pairs) {
                String[] keyValue = pair.split(":");
                if (keyValue.length == 2) {
                    String key = keyValue[0].trim().replace("\"", "");
                    String value = keyValue[1].trim().replace("\"", "");
                    if (key.equals("domain")) {
                        domain = value;
                    } else if (key.equals("ip")) {
                        ip = value;
                    }
                }
            }
            if (domain != null && ip != null) {
                result.put(domain, ip);
            }
        }
        return result;
    }

    private String formatJson(Map<String, String> data) {
        StringBuilder json = new StringBuilder("{\n    \"addresses\": [\n");
        for (Map.Entry<String, String> entry : data.entrySet()) {
            json.append("        {\n")
                    .append("            \"domain\": \"").append(entry.getKey()).append("\",\n")
                    .append("            \"ip\": \"").append(entry.getValue()).append("\"\n")
                    .append("        },\n");
        }
        if (!data.isEmpty()) {
            json.deleteCharAt(json.length() - 2); // Удаляем последнюю запятую
        }
        json.append("    ]\n}");
        return json.toString();
    }

    private void printDomainIpPairs(Map<String, String> data) {
        TreeMap<String, String> sortedMap = new TreeMap<>(data);
        sortedMap.forEach((domain, ip) -> System.out.println(domain + " - " + ip));    }

    private String getIpByDomain(Map<String, String> data, String domain) {
        return data.getOrDefault(domain, "Домен не найден");
    }

    private String getDomainByIp(Map<String, String> data, String ip) {
        for (Map.Entry<String, String> entry : data.entrySet()) {
            if (entry.getValue().equals(ip)) {
                return entry.getKey();
            }
        }
        return "IP-адрес не найден";
    }

    private boolean isUniqueDomain(Map<String, String> data, String domain) {
        return !data.containsKey(domain);
    }

    private boolean isUniqueIp(Map<String, String> data, String ip) {
        return !data.containsValue(ip);
    }

    private boolean isValidIpv4(String ip) {
        return ip.matches("^((25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\\.){3}(25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)$");
    }

    private String addDomainIpPair(Map<String, String> data, String domain, String ip) {
        if (!isUniqueDomain(data, domain)) return "Домен уже существует";
        if (!isUniqueIp(data, ip)) return "IP-адрес уже существует";
        if (!isValidIpv4(ip)) return "Некорректный IP-адрес";
        data.put(domain, ip);
        return "Пара добавлена";
    }

    private String removeDomainIpPair(Map<String, String> data, String key) {
        if (data.containsKey(key)) {
            data.remove(key);
            return "Пара удалена по домену";
        }
        for (Map.Entry<String, String> entry : data.entrySet()) {
            if (entry.getValue().equals(key)) {
                data.remove(entry.getKey());
                return "Пара удалена по IP-адресу";
            }
        }
        return "Домен или IP-адрес не найдены";
    }

    public static void main(String[] args) {

        String knownHostsPath = System.getenv("KNOWN_HOSTS_PATH");
        if (knownHostsPath == null) {
            System.out.println("Переменная окружения KNOWN_HOSTS_PATH не задана.");
            return;
        }

        Scanner scanner = new Scanner(System.in);
        System.out.print("Введите адрес SFTP-сервера: ");
        String host = scanner.nextLine();
        System.out.print("Введите порт SFTP-сервера: ");
        int port = scanner.nextInt();
        scanner.nextLine(); // Поглотить оставшийся символ новой строки
        System.out.print("Введите логин: ");
        String username = scanner.nextLine();
        System.out.print("Введите пароль: ");
        String password = scanner.nextLine();
        System.out.print("Введите путь к файлу на SFTP-сервере: ");
        String filePath = scanner.nextLine();

        try {

            SFTPClient client = new SFTPClient(host, port, username, password, filePath, knownHostsPath);
            boolean running = true;
            while (running) {
                System.out.println("\nВыберите действие:");
                System.out.println("1. Получить список пар 'домен – адрес'");
                System.out.println("2. Получить IP-адрес по доменному имени");
                System.out.println("3. Получить доменное имя по IP-адресу");
                System.out.println("4. Добавить новую пару 'домен – адрес'");
                System.out.println("5. Удалить пару 'домен – адрес'");
                System.out.println("6. Завершить работу");
                System.out.print("Ваш выбор: ");
                String choice = scanner.nextLine();

                Map<String, String> data = client.loadJsonFromFile();
                switch (choice) {
                    case "1":
                        client.printDomainIpPairs(data);
                        break;
                    case "2":
                        System.out.print("Введите доменное имя: ");
                        String domain = scanner.nextLine();
                        System.out.println(client.getIpByDomain(data, domain));
                        break;
                    case "3":
                        System.out.print("Введите IP-адрес: ");
                        String ip = scanner.nextLine();
                        System.out.println(client.getDomainByIp(data, ip));
                        break;
                    case "4":
                        System.out.print("Введите доменное имя: ");
                        String newDomain = scanner.nextLine();
                        System.out.print("Введите IP-адрес: ");
                        String newIp = scanner.nextLine();
                        System.out.println(client.addDomainIpPair(data, newDomain, newIp));
                        client.saveJsonToFile(data);
                        break;
                    case "5":
                        System.out.print("Введите доменное имя или IP-адрес: ");
                        String key = scanner.nextLine();
                        System.out.println(client.removeDomainIpPair(data, key));
                        client.saveJsonToFile(data);
                        break;
                    case "6":
                        running = false;
                        break;
                    default:
                        System.out.println("Неверный выбор");
                }
            }
            client.disconnect();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}