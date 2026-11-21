package mini.launcher;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.concurrent.ExecutionException;

public class LauncherUI extends JFrame {

    private final JComboBox<Path> autoDirsBox = new JComboBox<>();
    private final JTextField dirField = new JTextField();
    private final JButton browseBtn = new JButton("...");
    private final JButton rescanBtn = new JButton("Rescan");

    private final JComboBox<String> versionBox = new JComboBox<>();
    private final JButton refreshBtn = new JButton("Refresh");

    private final JCheckBox onlineBox = new JCheckBox("Microsoft Login (Placeholder)");
    private final JTextField userField = new JTextField("Player");

    private final JComboBox<String> javaBox = new JComboBox<>();
    private final JButton detectJavaBtn = new JButton("Detect");

    private final JButton launchBtn = new JButton("Launch Minecraft");
    private final JButton supportBtn = new JButton("⭐ Enjoying the app? Support the author!");
    private final JProgressBar progress = new JProgressBar();
    private final JTextArea logArea = new JTextArea(16, 75);

    public LauncherUI() {
        setTitle("Minecraft Launch MINI");
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setLayout(new BorderLayout(12, 12));
        ((JComponent) getContentPane()).setBorder(new EmptyBorder(15, 15, 15, 15));

        JPanel form = new JPanel(new GridBagLayout());
        GridBagConstraints gc = new GridBagConstraints();
        gc.insets = new Insets(6, 6, 6, 6);
        gc.fill = GridBagConstraints.HORIZONTAL;
        int row = 0;

        addRow(form, gc, row++, "Detected Directory:", autoDirsBox, rescanBtn);
        addRow(form, gc, row++, "Game Directory:", dirField, browseBtn);
        addRow(form, gc, row++, "Game Version:", versionBox, refreshBtn);
        addRow(form, gc, row++, "Java Path:", javaBox, detectJavaBtn);

        JPanel authPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        onlineBox.setFont(onlineBox.getFont().deriveFont(Font.BOLD));
        userField.setColumns(15);
        authPanel.add(onlineBox);
        authPanel.add(new JLabel("Username:"));
        authPanel.add(userField);
        gc.gridy = row;
        gc.gridx = 0; gc.gridwidth = 3;
        form.add(authPanel, gc);
        gc.gridwidth = 1;
        add(form, BorderLayout.NORTH);

        logArea.setEditable(false);
        logArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        logArea.setMargin(new Insets(8, 8, 8, 8));
        
        JScrollPane scroll = new JScrollPane(logArea);
        scroll.setBorder(BorderFactory.createTitledBorder("Log"));
        
        JPanel center = new JPanel(new BorderLayout(0, 6));
        center.add(scroll, BorderLayout.CENTER);
        
        progress.setStringPainted(true);
        progress.setString("Ready");
        progress.setPreferredSize(new Dimension(0, 6));
        progress.setIndeterminate(false);
        center.add(progress, BorderLayout.SOUTH);
        add(center, BorderLayout.CENTER);

        JPanel bottom = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        
        supportBtn.setVisible(false);
        supportBtn.setForeground(new Color(255, 105, 180));
        supportBtn.setFont(supportBtn.getFont().deriveFont(Font.BOLD));
        supportBtn.setContentAreaFilled(false);
        supportBtn.setBorderPainted(false);
        supportBtn.setCursor(new Cursor(Cursor.HAND_CURSOR));
        supportBtn.setFocusPainted(false);
        supportBtn.addActionListener(e -> {
            try {
                Desktop.getDesktop().browse(new URI("https://space.bilibili.com/3546930445551792?spm_id_from=333.337.0.0"));
            } catch (Exception ex) {
                log("Unable to open browser: " + ex.getMessage());
            }
        });

        launchBtn.setPreferredSize(new Dimension(180, 40));
        launchBtn.setFont(launchBtn.getFont().deriveFont(Font.BOLD, 14f));
        launchBtn.putClientProperty("JButton.buttonType", "roundRect");
        launchBtn.setBackground(new Color(60, 170, 90));
        launchBtn.setForeground(Color.WHITE);

        bottom.add(supportBtn);
        bottom.add(launchBtn);
        add(bottom, BorderLayout.SOUTH);

        autoDirsBox.addActionListener(e -> {
            Object sel = autoDirsBox.getSelectedItem();
            if (sel instanceof Path p) { dirField.setText(p.toString()); refreshVersions(); }
        });
        rescanBtn.addActionListener(e -> scanDirs());
        browseBtn.addActionListener(e -> onBrowse());
        refreshBtn.addActionListener(e -> refreshVersions());
        detectJavaBtn.addActionListener(e -> scanJava());
        launchBtn.addActionListener(e -> onLaunch());

        onlineBox.addActionListener(e -> {
            if (onlineBox.isSelected()) {
                launchBtn.setEnabled(false);
                progress.setString("Microsoft login unavailable");
                log("⚠ Microsoft login is not implemented. Launch is disabled; uncheck Microsoft Login to use offline mode.");
            } else {
                launchBtn.setEnabled(true);
                progress.setString("Ready");
            }
        });

        scanDirs();
        scanJava();
        pack();
        setLocationRelativeTo(null);
    }

    private void addRow(JPanel p, GridBagConstraints gc, int row,
                        String label, JComponent field, JComponent btn) {
        gc.gridy = row;
        gc.gridx = 0; gc.weightx = 0;
        p.add(new JLabel(label), gc);
        gc.gridx = 1; gc.weightx = 1;
        p.add(field, gc);
        if (btn != null) {
            gc.gridx = 2; gc.weightx = 0;
            p.add(btn, gc);
        }
    }

    private void scanDirs() {
        autoDirsBox.removeAllItems();
        List<Path> dirs = PathDetector.autoDetectGameDirs();
        for (Path d : dirs) autoDirsBox.addItem(d);
        if (!dirs.isEmpty()) {
            autoDirsBox.setSelectedIndex(0);
            dirField.setText(dirs.get(0).toString());
            refreshVersions();
            log("Automatically found " + dirs.size() + " .minecraft directory/directories");
        } else {
            log("No .minecraft directory found; please select one manually");
        }
    }

    private void scanJava() {
        javaBox.removeAllItems();
        List<Path> list = JavaDetector.detect();
        for (Path p : list) javaBox.addItem(p.toString());
        if (list.isEmpty()) {
            javaBox.addItem("No Java detected; enter the path manually");
            log("No Java detected; check PATH or JAVA_HOME");
        } else {
            log("Detected " + list.size() + " Java installation(s)");
        }
    }

    private void onBrowse() {
        JFileChooser fc = new JFileChooser();
        fc.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        if (fc.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            dirField.setText(fc.getSelectedFile().getAbsolutePath());
            refreshVersions();
        }
    }

    private void refreshVersions() {
        versionBox.removeAllItems();
        String t = dirField.getText().trim();
        if (t.isEmpty()) return;
        Path dir = Paths.get(t);
        if (!Files.exists(dir)) { log("Directory does not exist: " + dir); return; }
        List<String> vs = PathDetector.listVersions(dir);
        for (String v : vs) versionBox.addItem(v);
        log(vs.isEmpty() ? "No versions found" : "Found " + vs.size() + " version(s)");
    }

    private void onLaunch() {
        String dirText = dirField.getText().trim();
        Object verObj = versionBox.getSelectedItem();
        String version = verObj == null ? "" : verObj.toString();
        String username = userField.getText().trim();
        Object javaObj = javaBox.getSelectedItem();
        String javaExe = javaObj == null ? "" : javaObj.toString();

        if (dirText.isEmpty() || version.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Please select a game directory and version");
            return;
        }
        if (username.isEmpty()) username = "Player";
        if (javaExe.isEmpty() || !Files.exists(Paths.get(javaExe))) {
            JOptionPane.showMessageDialog(this, "Invalid Java path; please detect Java again");
            return;
        }

        AuthProvider auth = onlineBox.isSelected()
                ? new AuthProvider.Microsoft(username)
                : new AuthProvider.Offline(username);

        Path gameDir = Paths.get(dirText);
        String finalJava = javaExe;
        AuthProvider finalAuth = auth;

        launchBtn.setEnabled(false);
        progress.setIndeterminate(true);
        progress.setString("Launching...");
        log("========== Starting Launch ==========");

        SwingWorker<Integer, String> worker = new SwingWorker<>() {
            @Override
            protected Integer doInBackground() throws Exception {
                LaunchCore core = new LaunchCore(this::publish);
                return core.launch(gameDir, version, finalAuth, finalJava);
            }
            @Override
            protected void process(List<String> chunks) {
                for (String s : chunks) logArea.append(s + "\n");
                logArea.setCaretPosition(logArea.getDocument().getLength());
            }
            @Override
            protected void done() {
                progress.setIndeterminate(false);
                progress.setString("Ready");
                launchBtn.setEnabled(!onlineBox.isSelected());
                try {
                    int code = get();
                    logArea.append("===== Exit Code: " + code + " =====\n");
                    
                    if (code == 0) {
                        supportBtn.setVisible(true);
                        revalidate();
                        repaint();
                    }
                    
                } catch (InterruptedException ignored) {
                } catch (ExecutionException ex) {
                    Throwable cause = ex.getCause();
                    if (cause instanceof IllegalStateException
                            && "MISSING_NATIVES".equals(cause.getMessage())) {
                        JOptionPane.showMessageDialog(LauncherUI.this,
                                "Native libraries are missing!\n\nCheck for natives-* jars under .minecraft/libraries,\nor manually place dll/so/dylib files in versions/<ver>/natives/.",
                                "Launch Failed: Missing Natives", JOptionPane.ERROR_MESSAGE);
                    } else if (cause instanceof IllegalStateException
                            && "MSA_NOT_IMPLEMENTED".equals(cause.getMessage())) {
                        JOptionPane.showMessageDialog(LauncherUI.this,
                                "Microsoft login is not implemented yet.\n\nUncheck Microsoft Login to launch in offline mode.",
                                "Microsoft Login Unavailable", JOptionPane.WARNING_MESSAGE);
                    } else {
                        logArea.append("[Error] " + cause + "\n");
                    }
                }
                logArea.setCaretPosition(logArea.getDocument().getLength());
            }
        };
        worker.execute();
    }

    private void log(String s) {
        SwingUtilities.invokeLater(() -> {
            logArea.append(s + "\n");
            logArea.setCaretPosition(logArea.getDocument().getLength());
        });
    }
}