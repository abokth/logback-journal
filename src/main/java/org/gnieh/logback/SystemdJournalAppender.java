/*
 * This file is part of the logback-journal project.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.gnieh.logback;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.StackTraceElementProxy;
import ch.qos.logback.core.AppenderBase;
import ch.qos.logback.core.encoder.Encoder;

/**
 * An appender that send the events to systemd journal
 *
 * @author Lucas Satabin
 *
 */
public class SystemdJournalAppender extends AppenderBase<ILoggingEvent> {

    boolean logLocation = true;

    boolean logException = true;

    boolean logThreadName = true;

    String syslogIdentifier = "";

    Encoder<ILoggingEvent> encoder = null;

    @Override
    protected void append(ILoggingEvent event) {
        try {
            // get the message id if any
            Map<String, String> mdc = event.getMDCPropertyMap();

            List<Object> messages = new ArrayList<>();

            // the formatted human readable message
            if (encoder == null)
                messages.add(event.getFormattedMessage());
            else {
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                encoder.init(baos);
                encoder.doEncode(event);
                String message = baos.toString();
                messages.add(message);
            }

            // the log level
            messages.add("PRIORITY=%i");
            messages.add(levelToInt(event.getLevel()));

            if (event.getThrowableProxy() != null) {
                StackTraceElementProxy[] stack = event.getThrowableProxy()
                        .getStackTraceElementProxyArray();
                if (stack.length > 0) {

                    // the location information if any is available and it is
                    // enabled
                    if (logLocation) {
                        StackTraceElement elt = stack[0].getStackTraceElement();
                        messages.add("CODE_FILE=%s");
                        messages.add(elt.getFileName());
                        messages.add("CODE_LINE=%i");
                        messages.add(elt.getLineNumber());
                        messages.add("CODE_FUNC=%s.%s");
                        messages.add(elt.getClassName());
                        messages.add(elt.getMethodName());
                    }

                    // if one wants to log the exception name and message, just
                    // do it
                    if (logException) {
                        messages.add("EXN_NAME=%s");
                        messages.add(event.getThrowableProxy().getClassName());
                        messages.add("EXN_MESSAGE=%s");
                        messages.add(event.getThrowableProxy().getMessage());
                    }
                }
            }

            // log thread name if enabled
            if (logThreadName) {
                messages.add("THREAD_NAME=%s");
                messages.add(event.getThreadName());
            }

            // add a message id field if any is defined for this logging event
            if (mdc.containsKey(SystemdJournal.MESSAGE_ID)) {
                messages.add("MESSAGE_ID=%s");
                messages.add(mdc.get(SystemdJournal.MESSAGE_ID));
            }

            // override the syslog identifier string if set
            if (!syslogIdentifier.isEmpty()) {
                messages.add("SYSLOG_IDENTIFIER=%s");
                messages.add(syslogIdentifier);
            }

            // the vararg list is null terminated
            messages.add(null);

            SystemdJournalLibrary journald = SystemdJournalLibrary.INSTANCE;

            journald.sd_journal_send("MESSAGE=%s", messages.toArray());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private int levelToInt(Level l) {
        switch (l.toInt()) {
        case Level.TRACE_INT:
        case Level.DEBUG_INT:
            return 7;
        case Level.INFO_INT:
            return 6;
        case Level.WARN_INT:
            return 4;
        case Level.ERROR_INT:
            return 3;
        default:
            throw new IllegalArgumentException("Unknown level value: " + l);
        }
    }

    public boolean isLogLocation() {
        return logLocation;
    }

    public void setLogLocation(boolean logLocation) {
        this.logLocation = logLocation;
    }

    public boolean isLogThreadName() {
        return logThreadName;
    }

    public void setLogThreadName(boolean logThreadName) {
        this.logThreadName = logThreadName;
    }

    public boolean isLogException() {
        return logException;
    }

    public void setLogException(boolean logException) {
        this.logException = logException;
    }

    public String getSyslogIdentifier() {
        return syslogIdentifier;
    }

    public void setSyslogIdentifier(String syslogIdentifier) {
        this.syslogIdentifier = syslogIdentifier;
    }

    public Encoder<ILoggingEvent> getEncoder() {
        return encoder;
    }

    public void setEncoder(Encoder<ILoggingEvent> encoder) {
        this.encoder = encoder;
    }
}
