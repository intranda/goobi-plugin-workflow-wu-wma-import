package io.goobi.workflow.importer.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Queue;

import org.apache.commons.collections4.queue.CircularFifoQueue;

import lombok.AccessLevel;
import lombok.Getter;

/**
 * Live state of a running (or last finished) import: progress bar values, error count and the log
 * shown in the plugin GUI.
 * <p>
 * This class deliberately lives in the <b>lib</b> module. In Goobi the <b>base</b> plugin jar
 * (deployed to {@code plugins/workflow/}) is (re)loaded by a throwaway JSPF classloader on every
 * single visit of the plugin page ({@code NavigationForm.setPlugin ->
 * PluginLoader.getPluginByTitle -> PluginManagerFactory.createPluginManager}). A {@code static}
 * field in the base module is therefore a different field on every page entry and can never be
 * shared - which is why an earlier static-in-base attempt showed an empty progress bar and empty
 * log after re-entering the page even though the import kept running in its background thread.
 * <p>
 * The lib jar ({@code plugins/GUI/}) is on the shared Tomcat webapp classpath (see the
 * {@code PreResources} entry for {@code plugins/GUI/} in the workflow context) and is loaded
 * exactly once. An instance of this class is stored in the {@code HttpSession}, so every freshly
 * loaded base plugin instance re-attaches to the same running import and shows its progress and log
 * again after leaving, re-entering or reloading the page. Being session bound it is also isolated
 * per user, so two admins running imports in parallel do not overwrite each other's state.
 * <p>
 * The scalar fields are {@code volatile} because they are written by the import worker thread and
 * read by JSF request/render threads. The log is only accessed through synchronized methods, so the
 * worker thread can append while a render thread reads a consistent snapshot.
 */
@Getter
public class WuWmaImportState {

    /** Attribute key under which the state is stored in the HttpSession. */
    public static final String SESSION_ATTRIBUTE = "wuWmaImportState";

    private volatile boolean run = false;
    private volatile int progress = -1;
    private volatile int itemCurrent = 0;
    private volatile int itemsTotal = 0;
    private volatile int errors = 0;
    private volatile long lastPush = System.currentTimeMillis();

    // not exposed directly; only reachable through the synchronized methods below
    @Getter(AccessLevel.NONE)
    private final Queue<LogMessage> logQueue = new CircularFifoQueue<>(50000);

    /** Reset all live values for a fresh import run (clears progress bar, error count and log). */
    public synchronized void reset() {
        run = false;
        progress = -1;
        itemCurrent = 0;
        itemsTotal = 0;
        errors = 0;
        logQueue.clear();
    }

    public void setRun(boolean run) {
        this.run = run;
    }

    public void setProgress(int progress) {
        this.progress = progress;
    }

    public void setItemCurrent(int itemCurrent) {
        this.itemCurrent = itemCurrent;
    }

    public void incrementItemCurrent() {
        this.itemCurrent++;
    }

    public void setItemsTotal(int itemsTotal) {
        this.itemsTotal = itemsTotal;
    }

    public void incrementErrors() {
        this.errors++;
    }

    public void setLastPush(long lastPush) {
        this.lastPush = lastPush;
    }

    /** Append a log line. Synchronized so it is safe against a concurrent {@link #getLogMessages()}. */
    public synchronized void addLog(LogMessage message) {
        logQueue.add(message);
    }

    /**
     * Return a snapshot copy of the current log lines for rendering. A copy (rather than the live
     * queue) so JSF can iterate it without risking a ConcurrentModificationException while the
     * worker thread keeps appending.
     */
    public synchronized List<LogMessage> getLogMessages() {
        return new ArrayList<>(logQueue);
    }
}
