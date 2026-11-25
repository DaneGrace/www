import javax.swing.*;
import javax.swing.border.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.*;

/** EventCrew Swing client. Network I/O runs off the EDT; all UI updates use invokeLater. */
public class EventCrewClient extends JFrame {
    static final Color DEEP = new Color(0x2E1065), PRIMARY = new Color(0x6D28D9),
            SOFT = new Color(0xEDE9FE), ACCENT = new Color(0xA78BFA), BG = new Color(0xF5F3FF);

    JTextField hostF = new JTextField("localhost", 10), portF = new JTextField("5000", 5),
            nickF = new JTextField(10), inputF = new JTextField(), taskF = new JTextField();
    JButton connectB = btn("Connect"), sendB = btn("Send"), assignB = btn("Assign"),
            startB = btn("Start"), doneB = btn("Done");
    JToggleButton readyB = new JToggleButton("I'm Ready");
    JTextArea chat = new JTextArea();
    DefaultListModel<String> crewModel = new DefaultListModel<>();
    JComboBox<String> assigneeBox = new JComboBox<>();
    DefaultTableModel taskModel = new DefaultTableModel(new String[]{"#", "Task", "Assigned to", "Status"}, 0) {
        public boolean isCellEditable(int r, int c) { return false; }
    };
    JTable taskTable = new JTable(taskModel);
    JLabel status = new JLabel("Disconnected"), readyLabel = new JLabel("Ready: 0 / 0");

    Socket sock;
    PrintWriter out;
    ExecutorService sender;
    volatile boolean connected;
    String myNick = "";

    static String enc(String s) { return URLEncoder.encode(s, StandardCharsets.UTF_8); }
    static String dec(String s) { return URLDecoder.decode(s, StandardCharsets.UTF_8); }

    static JButton btn(String t) {
        JButton b = new JButton(t);
        b.setBackground(PRIMARY); b.setForeground(Color.WHITE);
        b.setOpaque(true); b.setBorderPainted(false); b.setFocusPainted(false);
        b.setFont(b.getFont().deriveFont(Font.BOLD));
        return b;
    }
    static JLabel white(String t) { JLabel l = new JLabel(t); l.setForeground(Color.WHITE); return l; }
    static Border titled(String t) {
        return BorderFactory.createTitledBorder(BorderFactory.createLineBorder(ACCENT), t,
                TitledBorder.LEFT, TitledBorder.TOP, new Font("SansSerif", Font.BOLD, 12), PRIMARY);
    }

    EventCrewClient() {
        super("EventCrew");
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setSize(1020, 660);
        setLocationRelativeTo(null);
        getContentPane().setBackground(BG);
        setLayout(new BorderLayout());

        // Header with connection controls
        JPanel head = new JPanel(new BorderLayout());
        head.setBackground(DEEP);
        head.setBorder(new EmptyBorder(10, 14, 10, 14));
        JLabel title = white("EventCrew");
        title.setFont(new Font("SansSerif", Font.BOLD, 24));
        JLabel sub = new JLabel("Plan. Assign. Go live.");
        sub.setForeground(ACCENT);
        JPanel tt = new JPanel(new GridLayout(2, 1)); tt.setOpaque(false); tt.add(title); tt.add(sub);
        JPanel conn = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 6)); conn.setOpaque(false);
        conn.add(white("Server IP")); conn.add(hostF);
        conn.add(white("Port")); conn.add(portF);
        conn.add(white("Nickname")); conn.add(nickF);
        conn.add(connectB);
        head.add(tt, BorderLayout.WEST); head.add(conn, BorderLayout.EAST);
        add(head, BorderLayout.NORTH);

        // Chat (left)
        chat.setEditable(false); chat.setLineWrap(true); chat.setWrapStyleWord(true);
        chat.setFont(new Font("SansSerif", Font.PLAIN, 14)); chat.setBackground(Color.WHITE);
        JPanel inputRow = new JPanel(new BorderLayout(6, 0)); inputRow.setOpaque(false);
        inputRow.add(inputF, BorderLayout.CENTER); inputRow.add(sendB, BorderLayout.EAST);
        JPanel left = new JPanel(new BorderLayout(0, 6));
        left.setBackground(BG); left.setBorder(titled("Crew Chat"));
        left.add(new JScrollPane(chat), BorderLayout.CENTER); left.add(inputRow, BorderLayout.SOUTH);

        // Side: crew + readiness, task board
        JList<String> crewList = new JList<>(crewModel);
        crewList.setBackground(SOFT);
        readyLabel.setForeground(DEEP); readyLabel.setFont(readyLabel.getFont().deriveFont(Font.BOLD));
        readyB.setBackground(ACCENT); readyB.setForeground(DEEP); readyB.setOpaque(true); readyB.setFocusPainted(false);
        JPanel crewP = new JPanel(new BorderLayout(0, 4)); crewP.setBackground(BG); crewP.setBorder(titled("Online Crew & Readiness"));
        crewP.setPreferredSize(new Dimension(360, 190));
        JPanel rdy = new JPanel(new BorderLayout()); rdy.setOpaque(false);
        rdy.add(readyLabel, BorderLayout.WEST); rdy.add(readyB, BorderLayout.EAST);
        crewP.add(new JScrollPane(crewList), BorderLayout.CENTER); crewP.add(rdy, BorderLayout.SOUTH);

        taskTable.getTableHeader().setBackground(PRIMARY); taskTable.getTableHeader().setForeground(Color.WHITE);
        taskTable.setSelectionBackground(ACCENT); taskTable.setRowHeight(24);
        taskTable.getColumnModel().getColumn(0).setMaxWidth(32);
        JPanel add = new JPanel(new BorderLayout(4, 0)); add.setOpaque(false);
        JPanel addR = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0)); addR.setOpaque(false);
        addR.add(assigneeBox); addR.add(assignB);
        add.add(taskF, BorderLayout.CENTER); add.add(addR, BorderLayout.EAST);
        JPanel upd = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0)); upd.setOpaque(false);
        upd.add(new JLabel("Selected task:")); upd.add(startB); upd.add(doneB);
        JPanel bottom = new JPanel(new GridLayout(2, 1, 0, 4)); bottom.setOpaque(false);
        bottom.add(add); bottom.add(upd);
        JPanel taskP = new JPanel(new BorderLayout(0, 6)); taskP.setBackground(BG); taskP.setBorder(titled("Task Board"));
        taskP.add(new JScrollPane(taskTable), BorderLayout.CENTER); taskP.add(bottom, BorderLayout.SOUTH);
        JPanel side = new JPanel(new BorderLayout(0, 6)); side.setBackground(BG);
        side.add(crewP, BorderLayout.NORTH); side.add(taskP, BorderLayout.CENTER);

        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, left, side);
        split.setDividerLocation(500); split.setBackground(BG); split.setBorder(new EmptyBorder(8, 8, 8, 8));
        add(split, BorderLayout.CENTER);
        status.setBorder(new EmptyBorder(4, 12, 4, 12)); status.setForeground(DEEP);
        add(status, BorderLayout.SOUTH);

        // Actions
        connectB.addActionListener(e -> { if (connected) send("MSG|" + enc("/exit")); else connect(); });
        sendB.addActionListener(e -> sendMsg());
        inputF.addActionListener(e -> sendMsg());
        assignB.addActionListener(e -> assign());
        taskF.addActionListener(e -> assign());
        startB.addActionListener(e -> setTask("DOING"));
        doneB.addActionListener(e -> setTask("DONE"));
        readyB.addActionListener(e -> send("READY|" + (readyB.isSelected() ? 1 : 0)));
        setUi(false);
    }

    void setUi(boolean on) {
        for (JComponent c : new JComponent[]{inputF, sendB, taskF, assignB, startB, doneB, assigneeBox, readyB}) c.setEnabled(on);
        hostF.setEnabled(!on); portF.setEnabled(!on); nickF.setEnabled(!on);
        connectB.setText(on ? "Disconnect" : "Connect"); connectB.setEnabled(true);
    }

    void append(String s) { chat.append(s + "\n"); chat.setCaretPosition(chat.getDocument().getLength()); }
    void err(String m) { JOptionPane.showMessageDialog(this, m, "EventCrew", JOptionPane.WARNING_MESSAGE); }

    // ---- Networking (never on the EDT) ----
    void connect() {
        String host = hostF.getText().trim(), nick = nickF.getText().trim();
        int port;
        if (host.isEmpty()) { err("Enter the server IP address."); return; }
        try {
            port = Integer.parseInt(portF.getText().trim());
            if (port < 1 || port > 65535) throw new NumberFormatException();
        } catch (NumberFormatException e) { err("Port must be a number from 1 to 65535."); return; }
        if (nick.isEmpty()) { err("Enter a nickname."); return; }
        connectB.setEnabled(false); status.setText("Connecting...");
        new Thread(() -> {
            Socket s = null;
            try {
                InetSocketAddress addr = new InetSocketAddress(host, port);
                if (addr.isUnresolved()) throw new UnknownHostException(host);
                s = new Socket();
                s.connect(addr, 4000);
                BufferedReader in = new BufferedReader(new InputStreamReader(s.getInputStream(), StandardCharsets.UTF_8));
                PrintWriter pw = new PrintWriter(new OutputStreamWriter(s.getOutputStream(), StandardCharsets.UTF_8), true);
                pw.println("NICK|" + enc(nick));
                s.setSoTimeout(5000);
                String first = in.readLine();
                s.setSoTimeout(0);
                if (first == null) throw new IOException("Server closed the connection.");
                String[] p = first.split("\\|", -1);
                if (p[0].equals("ERR")) {
                    s.close();
                    String m = dec(p[1]);
                    SwingUtilities.invokeLater(() -> { err(m); setUi(false); status.setText("Disconnected"); });
                    return;
                }
                final Socket fs = s;
                SwingUtilities.invokeLater(() -> onConnected(fs, pw, dec(p[1])));
                readLoop(in, fs);
            } catch (UnknownHostException e) { fail(s, "Unknown or invalid server address: " + host);
            } catch (SocketTimeoutException | ConnectException e) { fail(s, "Cannot reach server at " + host + ":" + port + ". Is it running?");
            } catch (IOException e) { fail(s, "Connection problem: " + e.getMessage()); }
        }, "net-connect").start();
    }

    void fail(Socket s, String msg) {
        try { if (s != null) s.close(); } catch (IOException ignored) {}
        SwingUtilities.invokeLater(() -> { err(msg); setUi(false); status.setText("Disconnected"); });
    }

    void onConnected(Socket s, PrintWriter pw, String nick) {
        sock = s; out = pw; myNick = nick; connected = true;
        sender = Executors.newSingleThreadExecutor();
        setUi(true);
        status.setText("Connected as " + nick);
        append("* Connected. Type /help for commands.");
        inputF.requestFocus();
    }

    void readLoop(BufferedReader in, Socket s) {
        try {
            String l;
            while ((l = in.readLine()) != null) { String f = l; SwingUtilities.invokeLater(() -> handle(f)); }
        } catch (IOException ignored) {}
        SwingUtilities.invokeLater(() -> lost(s));
    }

    void lost(Socket s) {
        if (s != sock || !connected) return;
        disconnect();
        append("! Connection lost. Reconnect when the server is back.");
        err("Connection to the server was lost.");
    }

    void disconnect() {
        connected = false;
        try { if (sock != null) sock.close(); } catch (IOException ignored) {}
        if (sender != null) sender.shutdownNow();
        crewModel.clear(); assigneeBox.removeAllItems(); taskModel.setRowCount(0);
        readyB.setSelected(false); readyLabel.setText("Ready: 0 / 0");
        setUi(false); status.setText("Disconnected");
    }

    void send(String line) {
        if (!connected) return;
        Socket s = sock;
        sender.execute(() -> { out.println(line); if (out.checkError()) SwingUtilities.invokeLater(() -> lost(s)); });
    }

    // ---- Handling server messages (on the EDT) ----
    void handle(String line) {
        String[] p = line.split("\\|", -1);
        switch (p[0]) {
            case "OK": myNick = dec(p[1]); status.setText("Connected as " + myNick); break;
            case "CHAT": append("[" + p[1] + "] " + dec(p[2]) + ": " + dec(p[3])); break;
            case "SYS": append("[" + p[1] + "] * " + dec(p[2])); break;
            case "ERR": append("! " + dec(p[1])); break;
            case "USERS": users(p.length > 1 ? p[1] : ""); break;
            case "TASKS": tasks(p.length > 1 ? p[1] : ""); break;
            case "BYE": disconnect(); append("* You left the chat."); break;
            default: break;
        }
    }

    void users(String s) {
        Object sel = assigneeBox.getSelectedItem();
        crewModel.clear(); assigneeBox.removeAllItems();
        int ready = 0, n = 0;
        if (!s.isEmpty()) for (String e : s.split(";")) {
            String[] f = e.split(",", -1);
            String nm = dec(f[0]); boolean rd = f[1].equals("1");
            crewModel.addElement((rd ? "\u2714 " : "\u25CB ") + nm + (nm.equals(myNick) ? " (you)" : ""));
            assigneeBox.addItem(nm); n++;
            if (rd) ready++;
            if (nm.equals(myNick)) readyB.setSelected(rd);
        }
        if (sel != null) assigneeBox.setSelectedItem(sel);
        readyLabel.setText("Ready: " + ready + " / " + n);
    }

    void tasks(String s) {
        taskModel.setRowCount(0);
        if (s.isEmpty()) return;
        for (String e : s.split(";")) {
            String[] f = e.split(",", -1);
            taskModel.addRow(new Object[]{f[0], dec(f[1]), dec(f[2]), f[3]});
        }
    }

    // ---- User actions ----
    void sendMsg() {
        String t = inputF.getText().trim();
        if (t.isEmpty()) { append("! Cannot send a blank message."); return; }
        send("MSG|" + enc(t));
        inputF.setText("");
    }

    void assign() {
        String t = taskF.getText().trim();
        Object who = assigneeBox.getSelectedItem();
        if (t.isEmpty() || who == null) { append("! Enter a task title and choose an assignee."); return; }
        send("TASK_ADD|" + enc(t) + "|" + enc(who.toString()));
        taskF.setText("");
    }

    void setTask(String st) {
        int r = taskTable.getSelectedRow();
        if (r < 0) { append("! Select a task first."); return; }
        send("TASK_STATUS|" + taskModel.getValueAt(taskTable.convertRowIndexToModel(r), 0) + "|" + st);
    }

    public static void main(String[] args) {
        try { UIManager.setLookAndFeel(UIManager.getCrossPlatformLookAndFeelClassName()); } catch (Exception ignored) {}
        SwingUtilities.invokeLater(() -> new EventCrewClient().setVisible(true));
    }
}
