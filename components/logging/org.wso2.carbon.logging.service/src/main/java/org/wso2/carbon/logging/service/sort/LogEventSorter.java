package org.wso2.carbon.logging.service.sort;

import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.Callable;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.wso2.carbon.logging.service.data.LogEvent;
import org.wso2.carbon.logging.service.util.LoggingConstants;

public class LogEventSorter implements Callable<List<LogEvent>> {

    private List<LogEvent> events;
    private List<LogEvent> preprocessedEvents;
    private static LogTaskThreadPoolExecuter executer;
    private static Log log = LogFactory.getLog(LogEventSorter.class);

    public LogEventSorter(List<LogEvent> events) {
        this.events = events;
        executer = new LogTaskThreadPoolExecuter();
        this.preprocessedEvents = new ArrayList<LogEvent>();

    }

    private List<LogEvent> getLogEvents() {
        return this.events;
    }

    private LogTaskThreadPoolExecuter getExecuter() {
        return executer;
    }

    public List<LogEvent> getPreprocessedEvents() {
        return preprocessedEvents;
    }

    private static List<LogEvent> sortLogs(List<LogEvent> logEvents, int left, int right) {
        if (left < right) {
            int initialPivot = left + ((right - left) / 2);
            int pivot = partition(logEvents, left, right, initialPivot);
            if (pivot > 0) {
                sortLogs(logEvents, left, pivot - 1);
            } else if (pivot < (logEvents.size() - 1)) {
                sortLogs(logEvents, pivot + 1, right);
            }
        }
        return logEvents;
    }

    private synchronized static int partition(List<LogEvent> logEvents, int left, int right, int pivot) {
        int storeIndex;
        long logTime = createDateObject(logEvents.get(pivot).getLogTime()).getTime();
        LogEvent logEvent = logEvents.get(right);
        logEvents.set(right, logEvents.get(pivot));
        logEvents.set(pivot, logEvent);
        storeIndex = left;
        for (int i = left; i < right; i++) {
            long t1 = createDateObject(logEvents.get(i).getLogTime()).getTime();
            if (t1 > logTime) {
                logEvent = logEvents.get(storeIndex);
                logEvents.set(storeIndex, logEvents.get(i));
                logEvents.set(i, logEvent);
                storeIndex = storeIndex + 1;
            }
        }
        logEvent = logEvents.get(storeIndex);
        logEvents.set(storeIndex, logEvents.get(right));
        logEvents.set(right, logEvent);
        return storeIndex;
    }


    private static Date createDateObject(String date) {
        Date d = null;
        DateFormat formatter;
        try {
            formatter = new SimpleDateFormat(LoggingConstants.DATE_TIME_FORMATTER);
            d = formatter.parse(date);
        } catch (ParseException e) {
            log.error("Illegal Date Format", e);
        }
        return d;
    }

    @Override
    public List<LogEvent> call() throws Exception {
        List<LogEvent> logEvents;
        if (events != null) {
            logEvents = sortLogs(events, 0, events.size() - 1);

        } else {
            logEvents = new ArrayList<LogEvent>();
        }
        return logEvents;
    }

}