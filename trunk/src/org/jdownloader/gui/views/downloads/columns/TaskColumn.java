package org.jdownloader.gui.views.downloads.columns;

import javax.swing.Icon;

import jd.controlling.packagecontroller.AbstractNode;
import jd.plugins.DownloadLink;
import jd.plugins.FilePackage;
import jd.plugins.LinkStatus;
import jd.plugins.PluginProgress;

import org.appwork.swing.exttable.columns.ExtTextColumn;
import org.jdownloader.gui.translate._GUI;
import org.jdownloader.images.NewTheme;

public class TaskColumn extends ExtTextColumn<AbstractNode> {

    /**
     * 
     */
    private static final long serialVersionUID = 1L;

    @Override
    public int getDefaultWidth() {
        return 180;
    }

    public boolean isPaintWidthLockIcon() {
        return false;
    }

    // @Override
    // public int getMaxWidth() {
    //
    // return 150;
    // }

    @Override
    public boolean isEnabled(AbstractNode obj) {
        return obj.isEnabled();
    }

    public TaskColumn() {
        super(_GUI._.StatusColumn_StatusColumn());
    }

    @Override
    protected Icon getIcon(AbstractNode value) {
        if (value instanceof DownloadLink) {
            LinkStatus ls = ((DownloadLink) value).getLinkStatus();

            PluginProgress prog = ((DownloadLink) value).getPluginProgress();
            if (prog != null && prog.getPercent() > 0.0 && prog.getPercent() < 100.0) {
                NewTheme.I().getIcon("update", 16);
            } else if (ls.getStatusIcon() != null) {
                return ls.getStatusIcon();
            } else if (ls.isFinished()) {
                return NewTheme.I().getIcon("true", 16);
            } else if (ls.isFailed() || (((DownloadLink) value).isAvailabilityStatusChecked() && !((DownloadLink) value).isAvailable())) {
                //
                return NewTheme.I().getIcon("false", 16);
            }
        } else if (value instanceof FilePackage) {
            if (((FilePackage) value).isFinished()) { return NewTheme.I().getIcon("true", 16); }
        }
        return null;
    }

    @Override
    public String getStringValue(AbstractNode value) {
        if (value instanceof DownloadLink) {
            return ((DownloadLink) value).getLinkStatus().getStatusString();

        } else if (value instanceof FilePackage) {

            if (((FilePackage) value).isFinished()) { return "Finished"; }

        }
        return "";
    }

}
