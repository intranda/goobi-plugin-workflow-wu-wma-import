package de.intranda.goobi.plugins;

import java.util.List;

public interface IWuWmaImportWorkflowPlugin {

    public List<IImportSet> getImportSets();

    public boolean isRun();

    public void startImport(IImportSet importset);
}
