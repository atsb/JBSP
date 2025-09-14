package gui;

import com.doom.bsp.BSP;
import com.doom.bsp.MakeNode;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.util.List;

/***
 * Main Swing GUI EntryPoint
 */
public class MainSwing extends JFrame {

    private final JTextField inField = new JTextField();
    private final JTextField outField = new JTextField();
    private final JTextArea log = new JTextArea();

    public MainSwing() {
        super("JBSP 1.1 - Doom Node Builder");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(840, 560);
        setLocationRelativeTo(null);
        setLayout(new BorderLayout());

        JPanel top = new JPanel(new GridBagLayout());
        GridBagConstraints gc = new GridBagConstraints();
        gc.insets = new Insets(6, 6, 6, 6);
        gc.fill = GridBagConstraints.NONE;
        gc.weightx = 0.0;

        JButton pickIn = new JButton("Input WAD");
        pickIn.addActionListener(e -> chooseFile(inField, true));
        JButton pickOut = new JButton("Output WAD");
        pickOut.addActionListener(e -> chooseFile(outField, false));
        JButton build = new JButton("Build Nodes");
        build.addActionListener(e -> runBuild());

        // Buttons
        gc.gridx = 0;
        gc.gridy = 0;
        gc.fill = GridBagConstraints.NONE;
        gc.weightx = 0;
        gc.anchor = GridBagConstraints.WEST;
        top.add(pickIn, gc);

        // Fields
        gc.gridx = 1;
        gc.gridy = 0;
        gc.fill = GridBagConstraints.HORIZONTAL;
        gc.weightx = 1.0;
        top.add(inField, gc);

        gc.gridx = 0;
        gc.gridy = 1;
        gc.fill = GridBagConstraints.NONE;
        gc.weightx = 0;
        gc.anchor = GridBagConstraints.WEST;
        top.add(pickOut, gc);
        gc.gridx = 1;
        gc.gridy = 1;
        gc.fill = GridBagConstraints.HORIZONTAL;
        gc.weightx = 1.0;
        top.add(outField, gc);

        // Build button
        gc.gridx = 0;
        gc.gridy = 2;
        gc.gridwidth = 2;
        gc.fill = GridBagConstraints.NONE;
        gc.weightx = 0;
        gc.anchor = GridBagConstraints.WEST;
        top.add(build, gc);

        add(top, BorderLayout.NORTH);

        log.setEditable(false);
        log.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        add(new JScrollPane(log), BorderLayout.CENTER);
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new MainSwing().setVisible(true));
    }

    private void chooseFile(JTextField field, boolean open) {
        JFileChooser fc = new JFileChooser(new File("."));
        int res = open ? fc.showOpenDialog(this) : fc.showSaveDialog(this);
        if (res == JFileChooser.APPROVE_OPTION) {
            field.setText(fc.getSelectedFile().getAbsolutePath());
        }
    }

    private void runBuild() {
        String in = inField.getText().trim();
        String out = outField.getText().trim();
        if (in.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Pick an input WAD first.");
            return;
        }
        if (out.isEmpty()) {
            out = new File(new File(in).getParentFile(), "output_nodes.wad").getAbsolutePath();
            outField.setText(out);
        }

        JTextAreaOutputStream.attachTo(log);
        log.append("Starting build…\n");
        final String outFinal = out;
        String mapName = chooseMap(in);
        if (mapName == null) return;

        new Thread(() -> {
            try {
                MakeNode.build(in, outFinal, mapName);
                log.append("\nDone. Wrote: " + outFinal + "\n");
                JOptionPane.showMessageDialog(this, "Build complete for " + mapName + ".\nSaved to: " + outFinal);
            } catch (Throwable t) {
                t.printStackTrace();
                JOptionPane.showMessageDialog(this, "Error: " + t, "Build failed", JOptionPane.ERROR_MESSAGE);
            }
        }, "builder").start();
    }

    private String chooseMap(String wadPath) {
        try {
            List<BSP.MapEntry> maps = BSP.listMaps(wadPath);
            if (maps.isEmpty()) {
                JOptionPane.showMessageDialog(this, "No map markers found in this WAD.", "No maps", JOptionPane.WARNING_MESSAGE);
                return null;
            }
            if (maps.size() == 1) {
                return maps.get(0).name();
            }
            Object sel = JOptionPane.showInputDialog(this,
                    "Select a map to build:",
                    "Choose map",
                    JOptionPane.QUESTION_MESSAGE,
                    null,
                    maps.toArray(),
                    maps.get(0));
            return sel == null ? null : sel.toString();
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Failed to scan WAD: " + ex, "Error", JOptionPane.ERROR_MESSAGE);
            return null;
        }
    }

    static class JTextAreaOutputStream extends java.io.OutputStream {
        private final JTextArea area;

        private JTextAreaOutputStream(JTextArea a) {
            area = a;
        }

        public static void attachTo(JTextArea a) {
            JTextAreaOutputStream out = new JTextAreaOutputStream(a);
            System.setOut(new java.io.PrintStream(out, true));
            System.setErr(new java.io.PrintStream(out, true));
        }

        @Override
        public void write(int b) {
            SwingUtilities.invokeLater(() -> area.append(String.valueOf((char) b)));
        }

        @Override
        public void write(byte[] b, int off, int len) {
            String s = new String(b, off, len);
            SwingUtilities.invokeLater(() -> area.append(s));
        }
    }
}
