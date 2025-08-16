import java.awt.*;
import java.awt.event.*;
import java.net.Socket;
import java.time.Duration;
import javax.swing.*;

public class RemoteControlLauncher {
    private final JFrame frame;
    private JToggleButton toggleButton;
    private JLabel statusLabel;     // device info
    private JLabel durationLabel;   // connection time
    private RemoteControlServer server;
    private final Timer uiTimer;

    public RemoteControlLauncher() {
        server = new RemoteControlServer();

        frame = new JFrame("Remote Control Server");
        frame.setSize(700, 250);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setLayout(new BorderLayout(10, 10));
        frame.setResizable(false);

        // Fonts
        Font durationFont = new Font("Segoe UI", Font.BOLD, 36);
        Font statusFont = new Font("Segoe UI", Font.PLAIN, 18);
        Font buttonFont = new Font("Segoe UI", Font.BOLD, 18);

        // Connection time label - large and centered top
        durationLabel = new JLabel("Connected Time: 00:00:00", SwingConstants.CENTER);
        durationLabel.setFont(durationFont);
        durationLabel.setForeground(new Color(0, 120, 0));

        // Device info label - smaller, center aligned below time
        statusLabel = new JLabel("Status: Disconnected", SwingConstants.CENTER);
        statusLabel.setFont(statusFont);
        statusLabel.setForeground(new Color(180, 0, 0));

        // Panel to hold duration and status vertically
        JPanel centerPanel = new JPanel();
        centerPanel.setLayout(new GridLayout(2, 1));
        centerPanel.add(durationLabel);
        centerPanel.add(statusLabel);

        frame.add(centerPanel, BorderLayout.CENTER);

        // Toggle button bottom right with padding
        toggleButton = new JToggleButton("Start Server");
        toggleButton.setFont(buttonFont);
        toggleButton.setFocusPainted(false);
        toggleButton.setBackground(new Color(50, 150, 250));
        toggleButton.setForeground(Color.WHITE);
        toggleButton.setBorder(BorderFactory.createEmptyBorder(10, 30, 10, 30));
        toggleButton.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        // Hover effect
        toggleButton.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent evt) {
                if (!toggleButton.isSelected())
                    toggleButton.setBackground(new Color(70, 170, 255));
            }

            @Override
            public void mouseExited(MouseEvent evt) {
                if (!toggleButton.isSelected())
                    toggleButton.setBackground(new Color(50, 150, 250));
            }
        });

        // Panel for button to right-align it with some padding
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 20, 10));
        buttonPanel.add(toggleButton);

        frame.add(buttonPanel, BorderLayout.SOUTH);

        // Button listener
        toggleButton.addItemListener(e -> {
            if (toggleButton.isSelected()) {
                toggleButton.setText("Stop Server");
                toggleButton.setBackground(new Color(200, 50, 50)); // red when active (stop)
                statusLabel.setForeground(new Color(0, 120, 0)); // green connected
                startServer();
            } else {
                toggleButton.setText("Start Server");
                toggleButton.setBackground(new Color(50, 150, 250)); // blue when stopped
                statusLabel.setForeground(new Color(180, 0, 0)); // red disconnected
                stopServer();
            }
        });

        // Timer updates connection duration every second
        uiTimer = new Timer(1000, e -> {
            if (toggleButton.isSelected()) {
                Duration d = server.getConnectionDuration();
                durationLabel.setText("Connected Time: " + formatDuration(d));
            } else {
                durationLabel.setText("Connected Time: 00:00:00");
            }
        });
        uiTimer.start();

        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
    }

    private void startServer() {
        new Thread(() -> {
            server.setConnectionListener(new RemoteControlServer.ConnectionListener() {
                @Override
                public void onClientConnected(Socket clientSocket) {
                    SwingUtilities.invokeLater(() -> {
                        statusLabel.setText("Status: Connected to " + clientSocket.getInetAddress().getHostAddress());
                        statusLabel.setForeground(new Color(0, 120, 0)); // green
                    });
                }

                @Override
                public void onClientDisconnected() {
                    SwingUtilities.invokeLater(() -> {
                        statusLabel.setText("Status: Disconnected");
                        statusLabel.setForeground(new Color(180, 0, 0)); // red
                        durationLabel.setText("Connected Time: 00:00:00");
                    });
                }
            });

            try {
                server.start();
            } catch (Exception e) {
                SwingUtilities.invokeLater(() -> {
                    toggleButton.setSelected(false);
                    toggleButton.setText("Start Server");
                    toggleButton.setBackground(new Color(50, 150, 250));
                    statusLabel.setText("Status: Error starting server");
                    statusLabel.setForeground(new Color(180, 0, 0));
                });
            }
        }).start();
    }

    private void stopServer() {
        server.stop();
        statusLabel.setText("Status: Disconnected");
        statusLabel.setForeground(new Color(180, 0, 0));
        durationLabel.setText("Connected Time: 00:00:00");
    }

    private String formatDuration(Duration duration) {
        long hours = duration.toHours();
        long minutes = duration.toMinutesPart();
        long seconds = duration.toSecondsPart();
        return String.format("%02d:%02d:%02d", hours, minutes, seconds);
    }
}
