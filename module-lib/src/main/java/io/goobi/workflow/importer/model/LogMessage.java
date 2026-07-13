package io.goobi.workflow.importer.model;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * A single log line shown in the import plugin GUI, together with its severity level. Lives in the
 * lib module so it is loaded by the shared webapp classloader and can safely be kept in the
 * {@link WuWmaImportState} that is stored in the HttpSession.
 */
@Data
@AllArgsConstructor
public class LogMessage {
    private String message;
    private int level;
}
