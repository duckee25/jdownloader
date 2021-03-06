package org.jdownloader.gui.views.linkgrabber.quickfilter;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;

import javax.swing.JLabel;
import javax.swing.JPopupMenu;
import javax.swing.KeyStroke;
import javax.swing.ListSelectionModel;
import javax.swing.event.TableModelEvent;
import javax.swing.event.TableModelListener;

import jd.controlling.IOEQ;
import jd.controlling.linkcrawler.CrawledLink;
import jd.controlling.linkcrawler.CrawledPackage;
import jd.controlling.packagecontroller.AbstractNode;
import jd.gui.swing.laf.LookAndFeelController;

import org.appwork.scheduler.DelayedRunnable;
import org.appwork.storage.config.ValidationException;
import org.appwork.storage.config.events.GenericConfigEventListener;
import org.appwork.storage.config.handler.BooleanKeyHandler;
import org.appwork.storage.config.handler.KeyHandler;
import org.appwork.swing.exttable.AlternateHighlighter;
import org.appwork.swing.exttable.ExtColumn;
import org.appwork.swing.exttable.ExtComponentRowHighlighter;
import org.appwork.swing.exttable.ExtTable;
import org.appwork.swing.exttable.columns.ExtCheckColumn;
import org.appwork.utils.ColorUtils;
import org.appwork.utils.swing.EDTRunner;
import org.jdownloader.gui.views.components.packagetable.PackageControllerTableModel;
import org.jdownloader.gui.views.components.packagetable.PackageControllerTableModelFilter;
import org.jdownloader.gui.views.linkgrabber.LinkGrabberTable;
import org.jdownloader.gui.views.linkgrabber.actions.ConfirmAction;
import org.jdownloader.gui.views.linkgrabber.addlinksdialog.LinkgrabberSettings;
import org.jdownloader.gui.views.linkgrabber.contextmenu.CreateDLCAction;
import org.jdownloader.gui.views.linkgrabber.contextmenu.MergeToPackageAction;
import org.jdownloader.gui.views.linkgrabber.contextmenu.RemoveNonSelectedAction;
import org.jdownloader.gui.views.linkgrabber.contextmenu.RemoveSelectionAction;
import org.jdownloader.settings.GraphicalUserInterfaceSettings;

public abstract class FilterTable extends ExtTable<Filter> implements PackageControllerTableModelFilter<CrawledPackage, CrawledLink>, GenericConfigEventListener<Boolean> {

    /**
     * 
     */
    private static final long   serialVersionUID = -5917220196056769905L;
    protected ArrayList<Filter> filters          = new ArrayList<Filter>();
    protected volatile boolean  enabled          = false;
    private HeaderInterface     header;
    private LinkGrabberTable    linkgrabberTable;
    private DelayedRunnable     delayedRefresh;

    private TableModelListener  listener;
    private BooleanKeyHandler   visibleKeyHandler;
    private Filter              filterException;

    protected static final long REFRESH_MIN      = 200l;
    protected static final long REFRESH_MAX      = 2000l;
    private static final Object LOCK             = new Object();

    public FilterTable(HeaderInterface hosterFilter, LinkGrabberTable table, BooleanKeyHandler visible) {
        super(new FilterTableModel());
        header = hosterFilter;
        this.visibleKeyHandler = visible;
        header.setFilterCount(0);
        this.linkgrabberTable = table;
        delayedRefresh = new DelayedRunnable(IOEQ.TIMINGQUEUE, REFRESH_MIN, REFRESH_MAX) {

            @Override
            public void delayedrun() {
                updateNow();

            }

        };

        listener = new TableModelListener() {

            public void tableChanged(TableModelEvent e) {
                delayedRefresh.run();
            }
        };
        this.setShowVerticalLines(false);
        this.setShowGrid(false);
        this.setShowHorizontalLines(false);
        this.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        this.setRowHeight(22);

        int c = LookAndFeelController.getInstance().getLAFOptions().getPanelHeaderColor();
        Color b2;
        Color f2;
        if (c >= 0) {
            b2 = new Color(c);
            f2 = new Color(LookAndFeelController.getInstance().getLAFOptions().getPanelHeaderForegroundColor());
        } else {
            b2 = getForeground();
            f2 = getBackground();
        }
        this.setBackground(new Color(LookAndFeelController.getInstance().getLAFOptions().getPanelBackgroundColor()));

        this.getExtTableModel().addExtComponentRowHighlighter(new ExtComponentRowHighlighter<Filter>(f2, b2, null) {

            @Override
            public boolean accept(ExtColumn<Filter> column, Filter value, boolean selected, boolean focus, int row) {
                return selected;
            }

        });

        this.addRowHighlighter(new AlternateHighlighter(null, ColorUtils.getAlphaInstance(new JLabel().getForeground(), 6)));

        // this.addRowHighlighter(new SelectionHighlighter(null, b2));
        // this.getExtTableModel().addExtComponentRowHighlighter(new
        // ExtComponentRowHighlighter<T>(f2, b2, null) {
        //
        // @Override
        // public boolean accept(ExtColumn<T> column, T value, boolean selected,
        // boolean focus, int row) {
        // return selected;
        // }
        //
        // });

        // this.addRowHighlighter(new AlternateHighlighter(null,
        // ColorUtils.getAlphaInstance(new JLabel().getForeground(), 6)));
        this.setIntercellSpacing(new Dimension(0, 0));

        visible.getEventSender().addListener(this);
        init();
        onConfigValueModified(null, visible.getValue());
        GraphicalUserInterfaceSettings.LINKGRABBER_SIDEBAR_ENABLED.getEventSender().addListener(this, true);

    }

    protected void onSelectionChanged() {
        updateSelection();
    }

    private void updateSelection() {
        // clear selection in other filter tables if we switched to a new one
        if (this.hasFocus()) {

            // LinkFilterSettings.CFG
            if (LinkgrabberSettings.QUICK_VIEW_SELECTION_ENABLED.getValue()) {
                ArrayList<Filter> selection = getSelectedFilters();
                ArrayList<AbstractNode> newSelection = getMatches(selection);

                getLinkgrabberTable().getExtTableModel().setSelectedObjects(newSelection);
            }
        }
    }

    private ArrayList<Filter> getSelectedFilters() {
        ArrayList<PackageControllerTableModelFilter<CrawledPackage, CrawledLink>> tableFilters = getLinkgrabberTable().getPackageControllerTableModel().getTableFilters();
        ArrayList<Filter> ret = new ArrayList<Filter>();
        for (PackageControllerTableModelFilter<CrawledPackage, CrawledLink> f : tableFilters) {
            if (f instanceof FilterTable) {
                ret.addAll(((FilterTable) f).getExtTableModel().getSelectedObjects());
            }
        }

        return ret;
    }

    public ArrayList<AbstractNode> getMatches(ArrayList<Filter> selection) {
        List<CrawledLink> all = getVisibleLinks();
        HashSet<CrawledPackage> packages = new HashSet<CrawledPackage>();
        CrawledLink link;
        main: for (Iterator<CrawledLink> it = all.iterator(); it.hasNext();) {
            link = it.next();
            for (Filter f : selection) {
                if (f.isFiltered(link)) {
                    if (!link.getParentNode().isExpanded()) packages.add(link.getParentNode());

                    continue main;
                }
            }

            it.remove();
        }

        ArrayList<AbstractNode> newSelection = new ArrayList<AbstractNode>(all);
        newSelection.addAll(packages);
        return newSelection;
    }

    protected JPopupMenu onContextMenu(final JPopupMenu popup, final Filter contextObject, final ArrayList<Filter> selection, final ExtColumn<Filter> column, final MouseEvent mouseEvent) {

        ArrayList<String> ret = new ArrayList<String>();
        for (Filter f : selection) {
            ret.add(f.getName());
        }

        popup.add(new EnabledAllAction(getExtTableModel().getSelectedObjects()));
        ArrayList<Filter> nonSel = new ArrayList<Filter>(getExtTableModel().getTableData());
        for (Filter f : getExtTableModel().getSelectedObjects()) {
            nonSel.remove(f);
        }

        // if (LinkgrabberSettings.QUICK_VIEW_SELECTION_ENABLED.getValue()) {
        ArrayList<AbstractNode> matches = getMatches(getSelectedFilters());
        popup.add(new ConfirmAction(false, matches));
        popup.add(new MergeToPackageAction(matches));
        popup.add(new CreateDLCAction(matches));
        popup.add(new RemoveNonSelectedAction(getLinkgrabberTable(), matches).toContextMenuAction());
        popup.add(new RemoveSelectionAction(getLinkgrabberTable(), matches).toContextMenuAction());
        // popup.add(new
        // RemoveIncompleteArchives(matches).toContextMenuAction());

        // }

        return popup;
    }

    protected void processMouseEvent(final MouseEvent e) {

        if (e.getID() == MouseEvent.MOUSE_PRESSED) {
            if (e.getButton() == MouseEvent.BUTTON1 && e.getClickCount() == 1) {
                final int row = this.rowAtPoint(e.getPoint());
                ExtColumn<Filter> col = this.getExtColumnAtPoint(e.getPoint());
                if (isRowSelected(row) && !(col instanceof ExtCheckColumn)) {
                    // clearSelection();
                    if (getSelectedRows().length > 1) {
                        getSelectionModel().setSelectionInterval(row, row);
                    } else {
                        getSelectionModel().removeSelectionInterval(row, row);
                    }

                    return;
                }
            }
        }
        super.processMouseEvent(e);

    }

    @Override
    protected void onSingleClick(MouseEvent e, Filter obj) {
        ArrayList<PackageControllerTableModelFilter<CrawledPackage, CrawledLink>> tableFilters = getLinkgrabberTable().getPackageControllerTableModel().getTableFilters();

        if (!e.isControlDown()) {
            for (PackageControllerTableModelFilter<CrawledPackage, CrawledLink> f : tableFilters) {
                if (f instanceof FilterTable && f != this) {
                    ((FilterTable) f).clearSelection();

                }
            }
        }
    }

    protected boolean processKeyBinding(final KeyStroke stroke, final KeyEvent evt, final int condition, final boolean pressed) {
        if (!pressed) { return super.processKeyBinding(stroke, evt, condition, pressed); }

        switch (evt.getKeyCode()) {
        case KeyEvent.VK_ENTER:
        case KeyEvent.VK_BACK_SPACE:
        case KeyEvent.VK_DELETE:
            new EnabledAllAction(getExtTableModel().getSelectedObjects()).actionPerformed(null);
            return true;

        }
        return false;
    }

    protected void updateNow() {
        reset();
        ArrayList<Filter> newData = updateQuickFilerTableData();
        for (Iterator<Filter> it = newData.iterator(); it.hasNext();) {
            if (it.next().getCounter() == 0) {
                // it.remove();
            }
        }
        setVisible(newData.size() > 0);
        filters = newData;
        if (visibleKeyHandler.getValue()) getExtTableModel()._fireTableStructureChanged(newData, true);

    }

    protected int getCountWithout(Filter filter, ArrayList<CrawledLink> filteredLinks) {
        ArrayList<PackageControllerTableModelFilter<CrawledPackage, CrawledLink>> tableFilters = getLinkgrabberTable().getPackageControllerTableModel().getTableFilters();
        int ret = 0;
        main: for (CrawledLink l : filteredLinks) {
            if (filter.isFiltered(l)) {
                filterException = filter;
                try {
                    for (PackageControllerTableModelFilter<CrawledPackage, CrawledLink> f : tableFilters) {
                        if (f.isFiltered(l)) {

                            continue main;
                        }
                    }
                } finally {
                    filterException = null;
                }
                ret++;
            }
        }
        return ret;
    }

    protected void init() {
    }

    public LinkGrabberTable getLinkgrabberTable() {
        return linkgrabberTable;
    }

    protected abstract ArrayList<Filter> updateQuickFilerTableData();

    public void onConfigValidatorError(KeyHandler<Boolean> keyHandler, Boolean invalidValue, ValidationException validateException) {
    }

    public void setVisible(final boolean aFlag) {

        new EDTRunner() {

            @Override
            protected void runInEDT() {
                header.setEnabled(aFlag);
                FilterTable.super.setVisible(aFlag && visibleKeyHandler.getValue());
            }
        };
    }

    protected List<CrawledLink> getVisibleLinks() {
        return ((PackageControllerTableModel<CrawledPackage, CrawledLink>) linkgrabberTable.getExtTableModel()).getAllChildrenNodes();
    }

    public void onConfigValueModified(KeyHandler<Boolean> keyHandler, Boolean newValue) {
        if (Boolean.TRUE.equals(newValue) && GraphicalUserInterfaceSettings.CFG.isLinkgrabberSidebarEnabled()) {
            enabled = true;
            linkgrabberTable.getPackageControllerTableModel().addFilter(this);

            this.linkgrabberTable.getModel().addTableModelListener(listener);
            super.setVisible(true);
        } else {
            this.linkgrabberTable.getModel().removeTableModelListener(listener);

            enabled = false;
            /* filter disabled */

            linkgrabberTable.getPackageControllerTableModel().removeFilter(this);
            super.setVisible(false);
        }
        updateAllFiltersInstant();
        linkgrabberTable.getPackageControllerTableModel().recreateModel(false);
    }

    protected void updateAllFiltersInstant() {
        ArrayList<PackageControllerTableModelFilter<CrawledPackage, CrawledLink>> tableFilters = getLinkgrabberTable().getPackageControllerTableModel().getTableFilters();

        for (PackageControllerTableModelFilter<CrawledPackage, CrawledLink> f : tableFilters) {
            if (f instanceof FilterTable) {
                ((FilterTable) f).updateNow();
            }
        }
    }

    // @Override
    // public boolean isFiltered(CrawledLink v) {
    // /*
    // * speed optimization, we dont want to get extension several times
    // */
    // if (enabled == false) return false;
    // String ext = Files.getExtension(v.getName());
    // ArrayList<Filter> lfilters = filters;
    // for (Filter filter : lfilters) {
    // if (filter.isEnabled()) continue;
    // if (((ExtensionFilter) filter).isFiltered(ext)) {
    // filter.setMatchCounter(filter.getMatchCounter() + 1);
    //
    // return true;
    // }
    // }
    // return false;
    // }
    public boolean isFiltered(CrawledLink e) {
        if (enabled == false) return false;
        ArrayList<Filter> lfilters = getAllFilters();
        for (Filter filter : lfilters) {
            if (filter == filterException) continue;
            if (filter.isEnabled()) {
                continue;
            }
            if (filter.isFiltered(e)) {

            return true; }
        }
        return false;
    }

    abstract ArrayList<Filter> getAllFilters();

    public boolean isFiltered(CrawledPackage v) {

        return false;
    }

    public void reset() {
        ArrayList<Filter> lfilters = filters;
        for (Filter filter : lfilters) {

            filter.setCounter(0);
        }
    }

}
