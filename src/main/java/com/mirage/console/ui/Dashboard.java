package com.mirage.console.ui;

import com.mirage.common.Alert;
import com.mirage.common.Classification;
import com.mirage.common.LedgerEntry;
import com.mirage.common.Severity;
import com.mirage.common.trace.TraceBacklog;
import com.mirage.common.trace.TraceEvent;
import com.mirage.common.trace.TraceLevel;
import com.mirage.console.AlertLedger;
import com.mirage.console.AttackerDirectory;
import com.mirage.console.CounterOffensive;
import com.mirage.console.ResponseController;
import com.mirage.console.ShellFilters;
import com.mirage.console.ThreatLevel;
import com.mirage.console.ThreatTracker;
import com.mirage.ui.shell.ShellTheme;
import com.mirage.ui.shell.ShellWindow;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Consumer;
import java.util.function.IntSupplier;

/**
 * The blue-team dashboard: a live network map, a severity-coloured alert feed, an
 * attacker dossier / session-replay / manual-response panel for the selected
 * source, a connection and ledger status line, and a one-click ledger integrity
 * check that highlights a broken link in red.
 *
 * <p>With {@link LiveOptions} carrying a trace bus it also drives the live demo:
 * a {@link ThreatPopup} per source that escalates with its alerts, an operation
 * shell for every operator action, a full pipeline shell, and the auto-response
 * switch (when off, the Console records and waits for the operator).
 */
public final class Dashboard extends JFrame {

    private final transient AlertLedger ledger;
    private final transient IntSupplier connectionCount;
    private final transient AttackerDirectory directory;
    private final transient CounterOffensive counter;
    private final transient ResponseController responses;
    private final transient LiveOptions live;
    private final transient TraceBacklog backlog;
    private final transient OperationRunner runner;
    private final transient ThreatTracker threats = new ThreatTracker();
    private final transient Map<String, Classification> latestTechnique = new HashMap<>();
    private final transient Map<String, ThreatPopup> popups = boundedPopups(MAX_POPUPS, ThreatPopup::dispose);
    private final transient Map<String, String> latestReason = new HashMap<>();

    private final AlertTableModel model = new AlertTableModel();
    /** Between status items; on a narrow window the items wrap onto more lines (FB-04). */
    private static final String SEPARATOR = "    |    ";
    private final JPanel status = new JPanel(new WrapLayout(FlowLayout.LEFT, 0, 5));
    private final JLabel consoleStatus = new JLabel();
    private final JLabel alertStatus = new JLabel();
    private final JLabel sensorStatus = new JLabel();
    private final JLabel autoStatus = new JLabel();
    /** On its own, so the value DEMO step 7 reads out is never cut off (FB-04). */
    private final JLabel ledgerStatus = new JLabel();
    private final NetworkMapPanel map;
    private final AttackerDetailPanel detail;
    private final JTable table = new JTable(model);
    private final JCheckBox autoBox = new JCheckBox("Auto-response");
    private String ledgerState = "—";
    private int popupsOpened;

    public Dashboard(AlertLedger ledger, IntSupplier connectionCount) {
        this(ledger, connectionCount, new AttackerDirectory(), new ResponseController(
                new com.mirage.common.ScopeGuard(List.of()), com.mirage.console.CommandSink.NONE), null);
    }

    public Dashboard(AlertLedger ledger, IntSupplier connectionCount,
                     AttackerDirectory directory, ResponseController responses) {
        this(ledger, connectionCount, directory, responses, null);
    }

    public Dashboard(AlertLedger ledger, IntSupplier connectionCount,
                     AttackerDirectory directory, ResponseController responses, CounterOffensive counter) {
        this(ledger, connectionCount, directory, responses, counter, LiveOptions.none());
    }

    public Dashboard(AlertLedger ledger, IntSupplier connectionCount, AttackerDirectory directory,
                     ResponseController responses, CounterOffensive counter, LiveOptions live) {
        super("Mirage Console — live");
        this.ledger = ledger;
        this.connectionCount = connectionCount;
        this.directory = directory;
        this.counter = counter;
        this.responses = responses;
        this.live = live == null ? LiveOptions.none() : live;
        if (this.live.trace() != null) {
            if (this.live.backlog() != null) {
                backlog = this.live.backlog(); // subscribed since start-up (B-33)
            } else {
                backlog = new TraceBacklog(5000);
                this.live.trace().subscribe(backlog);
            }
            runner = new OperationShells(backlog, this::perform);
            // A Sensor's ACK of a BLOCK neutralises the source (receive thread -> Swing thread).
            backlog.attach(this::onTrace, false);
        } else {
            backlog = null;
            runner = (op, ip, onResult) -> onResult.accept(perform(op, ip));
        }
        this.map = new NetworkMapPanel(this::select);
        this.detail = new AttackerDetailPanel(directory, runner);
        buildUi();
        showStoredAlerts();
        new Timer(2000, e -> refreshStatus()).start();
    }

    /**
     * FB-02: the alerts saved by earlier runs go in the feed and on the map, and the chain is
     * checked once now. So an alert edited in the database while the Console was down is red as
     * the dashboard opens. They raise no popup: those are for what happens in this run.
     */
    private void showStoredAlerts() {
        ledger.storedAlerts().forEach(model::addAlert);
        rebuildMap();
        verifyLedger();
    }

    /** Do one operator operation (any thread) and return its result line. */
    private String perform(Operation operation, String sourceIp) {
        return switch (operation) {
            case COUNTER -> counterFor(sourceIp);
            case COUNTER_ALL -> counterAllFor(sourceIp);
            default -> responses.issue(operation.command(), sourceIp);
        };
    }

    /**
     * What a counter-attack mirrors for a source (B-33): its most severe finding, the latest one
     * on a tie. A later port scan must not turn a honeytoken-replay counter into a scan.
     */
    static Classification counterPick(Classification current, Classification incoming) {
        return current == null || incoming.severity().ordinal() >= current.severity().ordinal()
                ? incoming : current;
    }

    /** Most threat popups open at once; past this the least recently escalated one is closed. */
    static final int MAX_POPUPS = 12;

    /**
     * A map that keeps at most {@code max} entries, ordered by use (put or get), and hands the
     * least recently used one to {@code onEvict} when a new one would go past the cap (B-33: one
     * popup per source, with its frame and shell timer, used to be kept for ever).
     */
    static <V> Map<String, V> boundedPopups(int max, java.util.function.Consumer<V> onEvict) {
        return new java.util.LinkedHashMap<>(16, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, V> eldest) {
                if (size() > max) {
                    onEvict.accept(eldest.getValue());
                    return true;
                }
                return false;
            }
        };
    }

    /** Turn the source's own detected technique back on it (lab range only). */
    private String counterFor(String sourceIp) {
        if (counter == null) {
            return "counter-offensive not configured in this view";
        }
        Classification technique;
        synchronized (latestTechnique) {
            technique = latestTechnique.get(sourceIp);
        }
        if (technique == null) {
            return "no technique observed for " + sourceIp + " yet";
        }
        return counter.counter(sourceIp, technique);
    }

    /** Turn several methods on the source at once, concurrently (lab range only). */
    private String counterAllFor(String sourceIp) {
        if (counter == null) {
            return "counter-offensive not configured in this view";
        }
        return counter.counterAll(sourceIp);
    }

    private void buildUi() {
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setLayout(new BorderLayout(8, 8));

        JScrollPane mapScroll = new JScrollPane(map);
        mapScroll.setPreferredSize(new Dimension(1100, 128));
        mapScroll.setBorder(BorderFactory.createTitledBorder("Network map"));
        add(mapScroll, BorderLayout.NORTH);

        table.setDefaultRenderer(Object.class, new SeverityRenderer(model));
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                int row = table.getSelectedRow();
                if (row >= 0 && row < model.getRowCount()) {
                    select(model.alertAt(row).sourceIp());
                }
            }
        });
        JScrollPane feed = new JScrollPane(table);
        feed.setBorder(BorderFactory.createTitledBorder("Alert feed"));

        // Half each, at any window width (FB-04: a divider pinned at 560 px squeezed one side).
        JSplitPane split = new ProportionalSplitPane(feed, detail, 0.5);
        add(split, BorderLayout.CENTER);

        JButton verify = new JButton("Verify ledger");
        verify.addActionListener(e -> verifyLedger());

        JPanel controls = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        if (live.autoResponse() != null) {
            autoBox.setSelected(live.autoResponse().isEnabled());
            autoBox.setToolTipText("On: respond to every alert instantly. Off: record it and wait for the operator.");
            autoBox.addActionListener(e -> setAutoResponse(autoBox.isSelected()));
            controls.add(autoBox);
        }
        if (backlog != null) {
            JButton pipeline = new JButton("Pipeline shell");
            pipeline.setToolTipText("Every step the Console takes: receive threads, engine, rules, ledger, responses");
            pipeline.addActionListener(e -> openPipelineShell());
            controls.add(pipeline);
        }
        controls.add(verify);

        status.add(consoleStatus);
        status.add(alertStatus);
        status.add(sensorStatus);
        status.add(autoStatus);
        status.add(ledgerStatus);
        JPanel south = new JPanel(new BorderLayout(8, 8));
        south.add(status, BorderLayout.CENTER);
        south.add(controls, BorderLayout.EAST);
        south.setBorder(BorderFactory.createEmptyBorder(2, 6, 4, 6));
        add(south, BorderLayout.SOUTH);

        setSize(WindowSizing.forScreen(1120, 680)); // smaller on a 1024x768 projector or a scaled laptop
        setLocationRelativeTo(null);
        refreshStatus();
    }

    /** Called from the engine thread via the AlertBus; hops onto the EDT. */
    public void onAlert(Alert alert) {
        synchronized (latestTechnique) {
            latestTechnique.put(alert.sourceIp(),
                    counterPick(latestTechnique.get(alert.sourceIp()), alert.classification()));
        }
        // Track the level on the engine thread, before any response is dispatched, so a
        // fast Sensor ACK can never overtake the alert that caused it.
        Optional<ThreatLevel> change = backlog == null ? Optional.empty() : threats.onAlert(alert);
        // Mark the escalation in the trace timeline right here, between the ledger line and the
        // response line, so the popup and pipeline shells show it in order (not on the bus).
        change.ifPresent(level -> backlog.accept(ThreatPopup.bannerLine(alert.sourceIp(), level)));
        String reason = alert.classification().label() + " (" + alert.classification().attackTechnique()
                + ", " + alert.classification().severity() + ")";
        SwingUtilities.invokeLater(() -> {
            model.addAlert(alert);
            latestReason.put(alert.sourceIp(), reason);
            rebuildMap();
            refreshStatus();
            change.ifPresent(level -> showThreat(alert.sourceIp(), level, false));
        });
    }

    /** Trace lines from the backlog (any thread): only an ACK of a block/unblock matters here. */
    private void onTrace(TraceEvent event) {
        threats.onTrace(event).ifPresent(level ->
                SwingUtilities.invokeLater(() -> showThreat(event.subject(), level, true)));
    }

    /**
     * @param ownBanner true when the banner line was not already put in the timeline
     *                  (ACK-driven changes, which arrive while the backlog is dispatching)
     */
    private void showThreat(String sourceIp, ThreatLevel level, boolean ownBanner) {
        ThreatPopup popup = popups.get(sourceIp);
        if (popup == null) {
            popup = new ThreatPopup(sourceIp, backlog, runner, this::inspect);
            int offset = 36 * (popupsOpened++ % 6);
            popup.setLocation(80 + offset, 80 + offset);
            popups.put(sourceIp, popup);
        }
        popup.escalate(level, latestReason.get(sourceIp), isAutoResponseOn(), ownBanner);
    }

    private void inspect(String sourceIp) {
        select(sourceIp);
        toFront();
    }

    private boolean isAutoResponseOn() {
        return live.autoResponse() != null && live.autoResponse().isEnabled();
    }

    private void setAutoResponse(boolean on) {
        live.autoResponse().setEnabled(on);
        if (live.trace() != null) {
            live.trace().line("console", "response", null, on ? TraceLevel.WARN : TraceLevel.INFO,
                    "operator switched auto-response " + (on ? "ON — responding to alerts instantly"
                            : "OFF — alerts are recorded, the operator responds"));
        }
        refreshStatus();
    }

    private void openPipelineShell() {
        ShellWindow shell = new ShellWindow(ShellTheme.console(), ShellFilters.pipeline());
        Consumer<TraceEvent> feed = shell::onTrace;
        JFrame frame = new JFrame("Mirage — Console pipeline (all sources)");
        frame.setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        frame.add(shell);
        frame.setSize(940, 600);
        frame.setLocationByPlatform(true);
        frame.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosed(WindowEvent e) {
                backlog.detach(feed);
                shell.close();
            }
        });
        backlog.attach(feed, true);
        frame.setVisible(true);
    }

    private void select(String sourceIp) {
        detail.showSource(sourceIp);
        map.setSelected(sourceIp);
    }

    private void verifyLedger() {
        int broken = ledger.firstBrokenIndex();
        if (broken < 0) {
            ledgerState = "verified";
            model.setBrokenAlert(null);
        } else {
            ledgerState = "BROKEN (entry " + broken + ")";
            List<LedgerEntry> entries = ledger.entries();
            model.setBrokenAlert(broken < entries.size() ? entries.get(broken).alert() : null);
        }
        refreshStatus();
    }

    private void rebuildMap() {
        Map<String, Severity> worst = new TreeMap<>();
        Map<String, Integer> counts = new TreeMap<>();
        for (Alert a : model.alerts()) {
            String ip = a.sourceIp();
            Severity sev = a.classification().severity();
            worst.merge(ip, sev, (x, y) -> x.ordinal() >= y.ordinal() ? x : y);
            counts.merge(ip, 1, Integer::sum);
        }
        Set<String> all = new LinkedHashSet<>(worst.keySet());
        all.addAll(directory.sources());

        List<NetworkMapPanel.Node> nodes = new ArrayList<>();
        for (String ip : all) {
            nodes.add(new NetworkMapPanel.Node(ip, worst.get(ip), counts.getOrDefault(ip, 0)));
        }
        nodes.sort(Comparator
                .comparingInt((NetworkMapPanel.Node n) -> n.severity() == null ? -1 : n.severity().ordinal())
                .reversed()
                .thenComparing(NetworkMapPanel.Node::sourceIp));
        map.setNodes(nodes);
    }

    private void refreshStatus() {
        consoleStatus.setVisible(live.consoleAddress() != null);
        consoleStatus.setText("Console at " + live.consoleAddress() + SEPARATOR);
        alertStatus.setText("Alerts: " + model.getRowCount() + SEPARATOR);
        sensorStatus.setText("Sensors connected: " + connectionCount.getAsInt() + SEPARATOR);
        autoStatus.setVisible(live.autoResponse() != null);
        autoStatus.setText("Auto-response: " + (isAutoResponseOn() ? "ON" : "OFF (operator)") + SEPARATOR);
        ledgerStatus.setText("Ledger: " + ledgerState);
    }

    private static final class SeverityRenderer extends DefaultTableCellRenderer {

        private final transient AlertTableModel model;

        SeverityRenderer(AlertTableModel model) {
            this.model = model;
        }

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean selected,
                                                       boolean focused, int row, int column) {
            Component c = super.getTableCellRendererComponent(table, value, selected, focused, row, column);
            if (row >= 0 && row < model.getRowCount()) {
                if (model.isBrokenRow(row)) {
                    c.setBackground(new Color(0xFF, 0x52, 0x52));
                    c.setForeground(Color.WHITE);
                    return c;
                }
                c.setForeground(Color.BLACK);
                if (!selected) {
                    c.setBackground(colorFor(model.alertAt(row).classification().severity()));
                }
            }
            return c;
        }

        private static Color colorFor(Severity severity) {
            return switch (severity) {
                case CRITICAL -> new Color(0xFF, 0xCD, 0xD2);
                case HIGH -> new Color(0xFF, 0xE0, 0xB2);
                case MEDIUM -> new Color(0xFF, 0xF9, 0xC4);
                case LOW -> new Color(0xE8, 0xF5, 0xE9);
            };
        }
    }
}
