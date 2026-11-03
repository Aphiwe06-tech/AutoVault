

package ui;

import core.BackupManager;
import model.BackupJob;
import model.BackupVersion;

import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingWorker;
import javax.swing.Timer;
import javax.swing.table.DefaultTableModel;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import static javax.swing.WindowConstants.DO_NOTHING_ON_CLOSE;

public class MainWindow extends JFrame {

    private static final String[] COLUMNS =
            {"Name", "Source", "Destination", "Every (min)", "Last run", "Versions"};

    private final BackupManager manager;
    private final JTable jobTable;
    private final DefaultTableModel tableModel;
    private final JFileChooser chooser = new JFileChooser();
    private final JLabel status = new JLabel("Ready.");
    private List<BackupJob> shownJobs = new ArrayList<>();  

    public MainWindow(BackupManager manager) {
        super("AutoVault - Backup Scheduler");
        if (manager == null) {
            throw new IllegalArgumentException("BackupManager is required.");
        }
        this.manager = manager;
        this.tableModel = new DefaultTableModel(COLUMNS, 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        };
        this.jobTable = new JTable(tableModel);
        this.jobTable.setAutoCreateRowSorter(true);          // click headers to sort
        buildLayout();
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            @Override public void windowClosing(WindowEvent e) { saveAndExit(); }
        });
        setSize(900, 420);
        setLocationRelativeTo(null);
        new Timer(5000, e -> refreshTable()).start();
    }

    public void show() {
        super.show();   // not setVisible(true): setVisible calls show() again and loops forever
        refreshTable();
    }

    private void buildLayout() {
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT));
        buttons.add(button("Add job", e -> addJob()));
        buttons.add(button("Backup now", e -> backupNow()));
        buttons.add(button("Versions / Restore", e -> showVersions()));
        buttons.add(button("Remove job", e -> removeJob()));
        buttons.add(button("Save", e -> save()));
        buttons.add(button("QA / Logging", e -> {
    AutoVaultRole6Prototype qaWindow =
            new AutoVaultRole6Prototype();

    qaWindow.setVisible(true);
}));

        add(buttons, BorderLayout.NORTH);
        add(new JScrollPane(jobTable), BorderLayout.CENTER);
        add(status, BorderLayout.SOUTH);
    }

    private JButton button(String text, java.awt.event.ActionListener l) {
        JButton b = new JButton(text);
        b.addActionListener(l);
        return b;
    }

    private File chooseFolder() {
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        return chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION
                ? chooser.getSelectedFile() : null;
    }

    private void refreshTable() {
        shownJobs = new ArrayList<>(manager.getJobs());
        // remember the selection by job id so a refresh does not lose it
        String selectedId = null;
        BackupJob sel = selectedJob();
        if (sel != null) selectedId = sel.getJobId();

        tableModel.setRowCount(0);
        for (BackupJob j : shownJobs) {
            tableModel.addRow(new Object[]{
                    j.getName(), j.getSourceDir(), j.getDestinationDir(),
                    j.getIntervalMinutes(),
                    j.getLastRun() == null ? "never" : j.getLastRun().toString().replace('T', ' '),
                    j.getVersions().size()});
        }
        if (selectedId != null) {
            for (int i = 0; i < shownJobs.size(); i++) {
                if (shownJobs.get(i).getJobId().equals(selectedId)) {
                    int view = jobTable.convertRowIndexToView(i);
                    jobTable.setRowSelectionInterval(view, view);
                }
            }
        }
    }

    private BackupJob selectedJob() {
        int view = jobTable.getSelectedRow();
        if (view < 0) return null;
        int model = jobTable.convertRowIndexToModel(view);
        return model < shownJobs.size() ? shownJobs.get(model) : null;
    }

    private BackupJob requireSelection() {
        BackupJob job = selectedJob();
        if (job == null) {
            JOptionPane.showMessageDialog(this, "Please select a job in the table first.");
        }
        return job;
    }


    private void addJob() {
        JTextField nameField = new JTextField(20);
        JTextField srcField = new JTextField(20);
        JTextField destField = new JTextField(20);
        JSpinner interval = new JSpinner(new SpinnerNumberModel(30, 1, 10080, 1));
        JTextField exclField = new JTextField("tmp,log");

        JPanel form = new JPanel(new GridLayout(0, 3, 6, 6));
        form.add(new JLabel("Name:"));            form.add(nameField);  form.add(new JLabel());
        form.add(new JLabel("Source folder:"));   form.add(srcField);
        form.add(button("Browse...", e -> pick(srcField)));
        form.add(new JLabel("Destination:"));     form.add(destField);
        form.add(button("Browse...", e -> pick(destField)));
        form.add(new JLabel("Every (minutes):")); form.add(interval);   form.add(new JLabel());
        form.add(new JLabel("Skip extensions:")); form.add(exclField);  form.add(new JLabel());

        if (JOptionPane.showConfirmDialog(this, form, "Add backup job",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE) != JOptionPane.OK_OPTION) {
            return;
        }
        try {
            File src = new File(srcField.getText().trim());
            if (!src.isDirectory()) {
                throw new IllegalArgumentException("Source folder does not exist.");
            }
            BackupJob job = new BackupJob(nameField.getText(), src,
                    new File(destField.getText().trim()), (Integer) interval.getValue());
            for (String ext : exclField.getText().split(",")) {
                job.addExcludedExtension(ext);
            }
            manager.addJob(job);
            manager.startAuto(job.getJobId());
            setStatus("Added job '" + job.getName() + "'.");
            refreshTable();
        } catch (IllegalArgumentException ex) {
            error("Could not add job", ex.getMessage());
        }
    }

    private void pick(JTextField target) {
        File f = chooseFolder();
        if (f != null) target.setText(f.getAbsolutePath());
    }

    private void backupNow() {
        BackupJob job = requireSelection();
        if (job == null) return;
        setStatus("Backing up '" + job.getName() + "'...");
        new SwingWorker<BackupVersion, Void>() {
            @Override protected BackupVersion doInBackground() {
                return manager.runBackup(job.getJobId());
            }
            @Override protected void done() {
                try {
                    BackupVersion made = get();
                    if (made == null) {
                        setStatus("No changes since the last backup. Nothing to do.");
                    } else {
                        setStatus("Backup complete: " + made);
                    }
                } catch (Exception ex) {
                    error("Backup failed", rootMessage(ex));
                }
                refreshTable();
            }
        }.execute();
    }

    private void showVersions() {
        BackupJob job = requireSelection();
        if (job == null) return;
        List<BackupVersion> versions = manager.listVersions(job.getJobId());
        if (versions.isEmpty()) {
            JOptionPane.showMessageDialog(this, "This job has no versions yet.");
            return;
        }
        JComboBox<BackupVersion> box = new JComboBox<>(versions.toArray(new BackupVersion[0]));
        int ok = JOptionPane.showConfirmDialog(this, box,
                "Choose a version to restore", JOptionPane.OK_CANCEL_OPTION);
        if (ok != JOptionPane.OK_OPTION) return;

        BackupVersion chosen = (BackupVersion) box.getSelectedItem();
        JOptionPane.showMessageDialog(this, "Next, choose the folder to restore into.");
        File target = chooseFolder();
        if (chosen == null || target == null) return;

        setStatus("Restoring...");
        new SwingWorker<Void, Void>() {
            @Override protected Void doInBackground() {
                manager.restore(job.getJobId(), chosen.getVersionId(), target);
                return null;
            }
            @Override protected void done() {
                try {
                    get();
                    setStatus("Restored to " + target.getAbsolutePath());
                } catch (Exception ex) {
                    error("Restore failed", rootMessage(ex));
                }
            }
        }.execute();
    }

    private void removeJob() {
        BackupJob job = requireSelection();
        if (job == null) return;
        if (JOptionPane.showConfirmDialog(this, "Remove job '" + job.getName() + "'?",
                "Confirm", JOptionPane.YES_NO_OPTION) == JOptionPane.YES_OPTION) {
            manager.stopAuto(job.getJobId());
            manager.removeJob(job.getJobId());
            
            setStatus("Job removed.");
            refreshTable();
        }
    }

    private void save() {
        try {
            manager.saveAll();
            setStatus("Jobs saved.");
        } catch (Exception ex) {
            error("Save failed", ex.getMessage());
        }
    }

    private void saveAndExit() {
        try {
            manager.saveAll();
        } catch (Exception ex) {
            if (JOptionPane.showConfirmDialog(this,
                    "Could not save jobs (" + ex.getMessage() + "). Exit anyway?",
                    "Save failed", JOptionPane.YES_NO_OPTION) != JOptionPane.YES_OPTION) {
                return;
            }
        }
        dispose();
        System.exit(0);
    }

    private void setStatus(String text) { status.setText(text); }

    private void error(String title, String message) {
        setStatus(title + ": " + message);
        JOptionPane.showMessageDialog(this, message, title, JOptionPane.ERROR_MESSAGE);
    }

    private static String rootMessage(Throwable t) {
        while (t.getCause() != null) t = t.getCause();
        return t.getMessage() == null ? t.toString() : t.getMessage();
    }
}
