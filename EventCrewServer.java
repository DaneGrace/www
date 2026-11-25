import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.*;

/** EventCrew server: one thread per client, shared state guarded by LOCK. */
public class EventCrewServer {
    static final int DEFAULT_PORT = 5000;
    static final Object LOCK = new Object();
    static final Map<String, Handler> users = new LinkedHashMap<>();
    static final List<String[]> tasks = new ArrayList<>(); // {id, title, assignee, status}
    static int nextId = 1;
    static final ExecutorService pool = Executors.newCachedThreadPool();

    static String now() { return LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss")); }
    static String enc(String s) { return java.net.URLEncoder.encode(s, StandardCharsets.UTF_8); }
    static String dec(String s) { return java.net.URLDecoder.decode(s, StandardCharsets.UTF_8); }

    public static void main(String[] args) throws IOException {
        int port = DEFAULT_PORT;
        if (args.length > 0) {
            try { port = Integer.parseInt(args[0]); }
            catch (NumberFormatException e) { System.err.println("Invalid port: " + args[0]); return; }
        }
        try (ServerSocket ss = new ServerSocket(port)) {
            System.out.println("EventCrew server running on " + InetAddress.getLocalHost().getHostAddress() + ":" + port);
            while (true) pool.execute(new Handler(ss.accept()));
        }
    }

    static String validate(String n, String self) {
        if (n == null || n.isBlank()) return "Nickname cannot be blank.";
        if (!n.matches("[A-Za-z0-9_\\-]{1,16}")) return "Nickname must be 1-16 letters, digits, _ or -.";
        for (String u : users.keySet())
            if (u.equalsIgnoreCase(n) && !u.equals(self)) return "Nickname '" + n + "' is already in use.";
        return null;
    }

    static void broadcast(String line) {
        synchronized (LOCK) { for (Handler h : users.values()) h.send(line); }
    }
    static void broadcastSys(String text) { broadcast("SYS|" + now() + "|" + enc(text)); }

    static void pushUsers() {
        synchronized (LOCK) {
            StringJoiner j = new StringJoiner(";");
            for (Handler h : users.values()) j.add(enc(h.nick) + "," + (h.ready ? 1 : 0));
            broadcast("USERS|" + j);
        }
    }
    static void pushTasks() {
        synchronized (LOCK) {
            StringJoiner j = new StringJoiner(";");
            for (String[] t : tasks) j.add(t[0] + "," + enc(t[1]) + "," + enc(t[2]) + "," + t[3]);
            broadcast("TASKS|" + j);
        }
    }

    static class Handler implements Runnable {
        final Socket sock;
        BufferedReader in;
        PrintWriter out;
        String nick;
        boolean ready;

        Handler(Socket s) { this.sock = s; }

        synchronized void send(String line) { if (out != null) out.println(line); } // one writer at a time
        void sys(String t) { send("SYS|" + now() + "|" + enc(t)); }
        void err(String t) { send("ERR|" + enc(t)); }
        void closeQuiet() { try { sock.close(); } catch (IOException ignored) {} }

        public void run() {
            try {
                in = new BufferedReader(new InputStreamReader(sock.getInputStream(), StandardCharsets.UTF_8));
                out = new PrintWriter(new OutputStreamWriter(sock.getOutputStream(), StandardCharsets.UTF_8), true);
                String line = in.readLine();
                String[] p = line == null ? new String[0] : line.split("\\|", -1);
                if (p.length < 2 || !p[0].equals("NICK")) { err("Expected NICK first."); return; }
                String name = dec(p[1]).trim();
                synchronized (LOCK) {
                    String e = validate(name, null);
                    if (e != null) { err(e); return; }
                    nick = name;
                    users.put(nick, this);
                    send("OK|" + enc(nick));
                    broadcastSys(nick + " joined the crew");
                    pushUsers();
                    pushTasks();
                }
                while ((line = in.readLine()) != null) handle(line);
            } catch (IOException ignored) {
            } finally {
                leave();
            }
        }

        void leave() {
            synchronized (LOCK) {
                if (nick != null && users.get(nick) == this) {
                    users.remove(nick);
                    broadcastSys(nick + " left the crew");
                    pushUsers();
                }
            }
            closeQuiet();
        }

        void handle(String line) {
            String[] p = line.split("\\|", -1);
            try {
                switch (p[0]) {
                    case "MSG": onMsg(dec(p[1]).trim()); break;
                    case "TASK_ADD": addTask(dec(p[1]).trim(), dec(p[2])); break;
                    case "TASK_STATUS": setStatus(p[1], p[2]); break;
                    case "READY": setReady(p[1].equals("1")); break;
                    default: err("Unknown message type.");
                }
            } catch (RuntimeException e) { err("Malformed message."); }
        }

        void onMsg(String t) {
            if (t.isEmpty()) { err("Cannot send a blank message."); return; }
            if (!t.startsWith("/")) { broadcast("CHAT|" + now() + "|" + enc(nick) + "|" + enc(t)); return; }
            String[] c = t.split("\\s+", 2);
            switch (c[0].toLowerCase()) {
                case "/name": rename(c.length > 1 ? c[1].trim() : ""); break;
                case "/exit": send("BYE"); closeQuiet(); break;
                case "/ready": setReady(!ready); break;
                case "/help":
                    sys("Commands: /name <newname>, /ready, /exit, /help");
                    sys("Use the Task Board to assign work and the Ready button to confirm.");
                    break;
                default: err("Unknown command. Type /help.");
            }
        }

        void rename(String n) {
            synchronized (LOCK) {
                String e = validate(n, nick);
                if (e != null) { err(e); return; }
                String old = nick;
                users.remove(old);
                nick = n;
                users.put(nick, this);
                for (String[] t : tasks) if (t[2].equals(old)) t[2] = n;
                send("OK|" + enc(nick));
                broadcastSys(old + " is now known as " + n);
                pushUsers();
                pushTasks();
            }
        }

        void addTask(String title, String assignee) {
            synchronized (LOCK) {
                if (title.isEmpty() || title.length() > 60) { err("Task title must be 1-60 characters."); return; }
                if (!users.containsKey(assignee)) { err("Assignee is not online."); return; }
                String id = String.valueOf(nextId++);
                tasks.add(new String[]{id, title, assignee, "TODO"});
                broadcastSys(nick + " assigned task #" + id + " \"" + title + "\" to " + assignee);
                pushTasks();
            }
        }

        void setStatus(String id, String st) {
            synchronized (LOCK) {
                if (!Set.of("TODO", "DOING", "DONE").contains(st)) { err("Invalid task status."); return; }
                for (String[] t : tasks) {
                    if (!t[0].equals(id)) continue;
                    if (!t[2].equals(nick)) { err("Only the assignee can update this task."); return; }
                    t[3] = st;
                    broadcastSys(nick + " set task #" + id + " to " + st);
                    pushTasks();
                    return;
                }
                err("Task not found.");
            }
        }

        void setReady(boolean r) {
            synchronized (LOCK) {
                ready = r;
                broadcastSys(nick + (r ? " is ready" : " is no longer ready"));
                pushUsers();
                boolean all = users.size() >= 2;
                for (Handler h : users.values()) all &= h.ready;
                if (all) broadcastSys("All crew members are ready. The event is GO!");
            }
        }
    }
}
