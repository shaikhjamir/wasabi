package com.intuit.wasabi.auditlog.impl;

import com.google.inject.Inject;
import com.google.inject.name.Named;
import com.intuit.wasabi.auditlog.AuditLog;
import com.intuit.wasabi.auditlogobjects.AuditLogAction;
import com.intuit.wasabi.auditlogobjects.AuditLogEntry;
import com.intuit.wasabi.auditlogobjects.AuditLogProperty;
import com.intuit.wasabi.authenticationobjects.UserInfo;
import com.intuit.wasabi.experimentobjects.Application;
import com.intuit.wasabi.repository.AuditLogRepository;
import org.apache.commons.lang3.StringUtils;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;

/**
 * Implements the AuditLog with a default implementation.
 */
public class AuditLogImpl implements AuditLog {

    private final AuditLogRepository repository;
    private int limit;

    /**
     * Constructs the AuditLogImpl. Should be called by Guice.
     *
     * @param repository the repository to query from.
     * @param limit      maximum audit log to fetch
     */
    @Inject
    public AuditLogImpl(final AuditLogRepository repository, final @Named("auditlog.fetchlimit") int limit) {
        this.repository = repository;
        this.limit = limit;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<AuditLogEntry> getAuditLogs(Application.Name applicationName, String filterMask, String sortOrder) {
        return filterAndSort(repository.getAuditLogEntryList(applicationName, limit), filterMask, sortOrder);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<AuditLogEntry> getAuditLogs(String filterMask, String sortOrder) {
        return filterAndSort(repository.getCompleteAuditLogEntryList(limit), filterMask, sortOrder);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<AuditLogEntry> getGlobalAuditLogs(String filterMask, String sortOrder) {
        return filterAndSort(repository.getGlobalAuditLogEntryList(limit), filterMask, sortOrder);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<AuditLogEntry> filter(List<AuditLogEntry> auditLogEntries, String filterMask) {
        if (StringUtils.isBlank(filterMask)) {
            return auditLogEntries;
        }

        String[] mask = prepareMask(filterMask);
        boolean filterFullText = !mask[0].contains("=");
        String fullTextPattern = null;
        if (filterFullText) {
            fullTextPattern = mask[0].startsWith("\\-") ? mask[0].substring(2, mask[0].length()) : mask[0];
        }

        Map<String, String> optionsMap = new HashMap<>();

        for (Iterator<AuditLogEntry> iter = auditLogEntries.iterator(); iter.hasNext(); ) {
            AuditLogEntry auditLogEntry = iter.next();
            boolean remove = false;

            // filter for each key value pair until none is left or the entry should be removed
            for (int i = filterFullText ? 1 : 0; i < mask.length && !remove; ++i) {
                // determine key and value, fail if not well formatted
                String[] keyValue = mask[i].split("=");
                String options = "";
                if (keyValue.length > 2) { // return empty list if invalid
                    return Collections.emptyList();
                } else if (keyValue.length == 1) { // skip empty filters
                    continue;
                } else {
                    // options
                    if (keyValue[1].startsWith("{") && keyValue[1].contains("}")) {
                        options = keyValue[1].substring(1, keyValue[1].indexOf("}"));
                        keyValue[1] = keyValue[1].substring(keyValue[1].indexOf("}") + 1);
                        optionsMap.put(keyValue[0], options);
                    }
                }
                String pattern = keyValue[1].startsWith("\\-") ? keyValue[1].substring(2, keyValue[1].length()) : keyValue[1];
                remove = !singleFieldSearch(auditLogEntry, keyValue[0], pattern, options, !keyValue[1].startsWith("\\-"));
            }

            if (filterFullText && !remove) {
                remove = !fullTextSearch(auditLogEntry, fullTextPattern, optionsMap, !mask[0].startsWith("\\-"));
            }

            if (remove) {
                iter.remove();
            }
        }

        return auditLogEntries;
    }

    /**
     * Prepares the filter mask as it is defined by the rules described in {@link #filter(List, String)}.
     *
     * @param filterMask the filter mask
     * @return the individual filters
     */
    private String[] prepareMask(String filterMask) {
        String filterMaskEsc = filterMask.replaceAll(",(" + StringUtils.join(AuditLogProperty.keys(), "|") + ")+", "\\\\,$1");
        return filterMaskEsc.toLowerCase().split("\\\\,");
    }

    /**
     * Calls all distinguishing getters of the auditLogEntry until a match is found and returns {@code filter}.
     * If no match is found, {@code !filter} is returned.
     * Note: This method is case-insensitive.
     *
     * @param auditLogEntry the auditlog entry
     * @param pattern the pattern to search
     * @param filter the return value on success
     * @return {@code filter} on match, {@code !filter} otherwise
     */
    private boolean fullTextSearch(AuditLogEntry auditLogEntry, String pattern, Map<String, String> options, boolean filter) {
        for (String key : AuditLogProperty.keys()) {
            if (singleFieldSearch(auditLogEntry, key, pattern, options.get(key), filter) == filter) {
                return filter;
            }
        }
        if (contains(auditLogEntry.getExperimentId(), pattern)) return filter;
        return !filter;
    }

    /**
     * Returns true iff both, container and contained, are not blank and if
     * {@code container.toString().toLowerCase().contains(contained.toString().toLowerCase())}.
     * Note: This method is case-insensitive!
     *
     * @param container the string to search in
     * @param contained the string to search for
     * @return true if container contains contained
     */
    private boolean contains(Object container, Object contained) {
        if (container == null || contained == null) {
            return false;
        }
        String s1 = container.toString().toLowerCase();
        String s2 = contained.toString().toLowerCase();
        return s1.contains(s2);
    }
    
    /**
     * Calls the getter identified by {@code key.toLowerCase()} of the auditLogEntry returns {@code filter} on success.
     * If no match is found, {@code !filter} is returned.
     *
     * The allowed keys and their fields are:
     * <ul>
     *     <li>firstname {@link AuditLogEntry#getUser()}{@link UserInfo#getFirstName()}</li>
     *     <li>lastname {@link AuditLogEntry#getUser()}{@link UserInfo#getLastName()}</li>
     *     <li>username {@link AuditLogEntry#getUser()}{@link UserInfo#getUsername()} and {@link UserInfo#getUserId()}</li>
     *     <li>mail {@link AuditLogEntry#getUser()}{@link UserInfo#getEmail()}</li>
     *     <li>action {@link AuditLogEntry#getAction()} and {@link AuditLogAction#getDescription(AuditLogEntry)}</li>
     *     <li>desc {@link AuditLogEntry#getAction()} and {@link AuditLogAction#getDescription(AuditLogEntry)}</li>
     *     <li>experiment {@link AuditLogEntry#getExperimentLabel()}</li>
     *     <li>bucket {@link AuditLogEntry#getBucketLabel()}</li>
     *     <li>app {@link AuditLogEntry#getApplicationName()}</li>
     *     <li>time {@link AuditLogEntry#getTimeString()}</li>
     *     <li>attr {@link AuditLogEntry#getChangedProperty()}</li>
     *     <li>before {@link AuditLogEntry#getBefore()}</li>
     *     <li>after {@link AuditLogEntry#getAfter()}</li>
     *     <li>fullname {@link AuditLogEntry#getUser()}{@link UserInfo#getFirstName()} combined with {@link UserInfo#getLastName()}</li>
     * </ul>
     * Note: This method is case-insensitive!
     *
     * @param auditLogEntry the auditLogEntry
     * @param key the key determining the checked field
     * @param pattern the pattern to search
     * @param filter the return value on success
     * @return {@code filter} on match, {@code !filter} otherwise
     */
    /*test*/ boolean singleFieldSearch(AuditLogEntry auditLogEntry, String key, String pattern, String options, boolean filter) {
        AuditLogProperty property = AuditLogProperty.forKey(key);
        boolean filtered = !filter;
        
        if (property == null) {
            return filtered;
        }
        switch (property) {
            case FIRSTNAME: filtered = contains(auditLogEntry.getUser().getFirstName(), pattern) ? filter : !filter;
                break;
            case LASTNAME: filtered = contains(auditLogEntry.getUser().getLastName(), pattern) ? filter : !filter;
                break;
            case USERNAME: filtered = (contains(auditLogEntry.getUser().getUsername(), pattern) || (contains(auditLogEntry.getUser().getUserId(), pattern)) ? filter : !filter);
                break;
            case MAIL: filtered = contains(auditLogEntry.getUser().getEmail(), pattern) ? filter : !filter;
                break;
            case ACTION: // fall through
            case DESCRIPTION: filtered = (contains(auditLogEntry.getAction(), pattern) ? filter : !filter) || (contains(AuditLogAction.getDescription(auditLogEntry), pattern) ? filter : !filter);
                break;
            case EXPERIMENT: filtered = contains(auditLogEntry.getExperimentLabel(), pattern) ? filter : !filter;
                break;
            case BUCKET: filtered = contains(auditLogEntry.getBucketLabel(), pattern) ? filter : !filter;
                break;
            case APP: filtered = contains(auditLogEntry.getApplicationName(), pattern) ? filter : !filter;
                break;
            case TIME:
                filtered = contains(formatDateLikeUI(auditLogEntry.getTime(), StringUtils.isBlank(options) ? "+0000" : options), pattern) ? filter : !filter;
                break;
            case ATTR: filtered = contains(auditLogEntry.getChangedProperty(), pattern) ? filter : !filter;
                break;
            case BEFORE: filtered = contains(auditLogEntry.getBefore(), pattern) ? filter : !filter;
                break;
            case AFTER: filtered = contains(auditLogEntry.getAfter(), pattern) ? filter : !filter;
                break;
            case USER: filtered = contains(auditLogEntry.getUser().getFirstName() + " " + auditLogEntry.getUser().getLastName(), pattern) ? filter : !filter;
                break;
            default:
                break;
        }
        return filtered;
    }

    private String formatDateLikeUI(Calendar date, String timeZoneOffset) {
        TimeZone timeZone = TimeZone.getTimeZone("GMT" + timeZoneOffset);
        SimpleDateFormat sdf = new SimpleDateFormat("MMM d, YYYY HH:mm:ss a");
        sdf.setTimeZone(timeZone);
        return sdf.format(date.getTime());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<AuditLogEntry> sort(List<AuditLogEntry> auditLogEntries, final String sortOrder) {
        if (StringUtils.isBlank(sortOrder) || "-time".equalsIgnoreCase(sortOrder)) {
            return auditLogEntries;
        }
        Collections.sort(auditLogEntries, new Comparator<AuditLogEntry>() {

            private final int RIGHT_NULL = -1;
            private final int LEFT_NULL = 1;
            private final int NOT_NULL = 2;
            private final int BOTH_NULL = 0;

            /**
             * Compares to results for null values. If both are not null or both are null, this returns 0.
             * If o1 is not null but o2 is, it returns -1. If o2 is not null but o1 is, it returns 1.
             * The descending flag flips the results to make sure "null" values are always at the end.
             *
             * @param o1 an object
             * @param o2 another object
             * @return -1, 0, 1 depending on null status
             */
            private int compareNull(Object o1, Object o2, boolean descending) {
                if (o1 != null && o2 != null) {
                    return NOT_NULL;
                }
                if (o1 != null) {
                    return descending ? LEFT_NULL : RIGHT_NULL;
                }
                if (o2 != null) {
                    return descending ? RIGHT_NULL : LEFT_NULL;
                }
                return BOTH_NULL;
            }

            /**
             * {@inheritDoc}
             */
            @Override
            public int compare(AuditLogEntry o1, AuditLogEntry o2) {
                int result = 0;
                for (String sort : sortOrder.toLowerCase().split(",")) {
                    boolean descending = sort.contains("-");

                    AuditLogProperty property = AuditLogProperty.forKey(descending ? sort.substring(1) : sort);
                    if (property == null) {
                        continue;
                    }
                    switch (property) {
                        case FIRSTNAME:
                            if ((result = compareNull(o1.getUser().getFirstName(), o2.getUser().getFirstName(), descending)) == NOT_NULL) {
                                result = o1.getUser().getFirstName().compareToIgnoreCase(o2.getUser().getFirstName());
                            }
                            break;
                        case LASTNAME:
                            if ((result = compareNull(o1.getUser().getLastName(), o2.getUser().getLastName(), descending)) == NOT_NULL) {
                                result = o1.getUser().getLastName().compareToIgnoreCase(o2.getUser().getLastName());
                            }
                            break;
                        case USER: case USERNAME:
                            if ((result = compareNull(o1.getUser().getUsername(), o2.getUser().getUsername(), descending)) == NOT_NULL) {
                                result = o1.getUser().getUsername().toString().compareToIgnoreCase(o2.getUser().getUsername().toString());
                            }
                            if (result == 0 && (result = compareNull(o1.getUser().getUserId(), o2.getUser().getUserId(), descending)) == NOT_NULL) {
                                    result = o1.getUser().getUserId().compareToIgnoreCase(o2.getUser().getUserId());
                            }
                            break;
                        case MAIL:
                            if ((result = compareNull(o1.getUser().getEmail(), o2.getUser().getEmail(), descending)) == NOT_NULL) {
                                result = o1.getUser().getEmail().compareToIgnoreCase(o2.getUser().getEmail());
                            }
                            break;
                        case ACTION:
                            if ((result = compareNull(o1.getAction(), o2.getAction(), descending)) == NOT_NULL) {
                                result = o1.getAction().compareTo(o2.getAction());
                            }
                            break;
                        case EXPERIMENT:
                            if ((result = compareNull(o1.getExperimentLabel(), o2.getExperimentLabel(), descending)) == NOT_NULL) {
                                result = o1.getExperimentLabel().toString().compareToIgnoreCase(o2.getExperimentLabel().toString());
                            }
                            break;
                        case BUCKET:
                            if ((result = compareNull(o1.getBucketLabel(), o2.getBucketLabel(), descending)) == NOT_NULL) {
                                result = o1.getBucketLabel().toString().compareToIgnoreCase(o2.getBucketLabel().toString());
                            }
                            break;
                        case APP:
                            if ((result = compareNull(o1.getApplicationName(), o2.getApplicationName(), descending)) == NOT_NULL) {
                                result = o1.getApplicationName().toString().compareToIgnoreCase(o2.getApplicationName().toString());
                            }
                            break;
                        case TIME:
                            if ((result = compareNull(o1.getTime(), o2.getTime(), descending)) == NOT_NULL) {
                                result = o1.getTime().compareTo(o2.getTime());
                            }
                            break;
                        case ATTR:
                            if ((result = compareNull(o1.getChangedProperty(), o2.getChangedProperty(), descending)) == NOT_NULL) {
                                result = o1.getChangedProperty().compareToIgnoreCase(o2.getChangedProperty());
                            }
                            break;
                        case BEFORE:
                            if ((result = compareNull(o1.getBefore(), o2.getBefore(), descending)) == NOT_NULL) {
                                result = o1.getBefore().compareToIgnoreCase(o2.getBefore());
                            }
                            break;
                        case AFTER:
                            if ((result = compareNull(o1.getAfter(), o2.getAfter(), descending)) == NOT_NULL) {
                                result = o1.getAfter().compareToIgnoreCase(o2.getAfter());
                            }
                            break;
                        case DESCRIPTION:
                            String desc1 = AuditLogAction.getDescription(o1);
                            String desc2 = AuditLogAction.getDescription(o2);
                            result = desc1.compareToIgnoreCase(desc2);
                            break;
                        default:
                            break;
                    }
                    if (result != 0) {
                        if (descending) {
                            result = -result;
                        }
                        break;
                    }

                }
                return result;
            }
        });
        return auditLogEntries;
    }

    /**
     * Filters first and sorts then by subsequent calls to {@link #filter(List, String)} and {@link #sort(List, String)}.
     * Note: This method is case-insensitive!
     *
     * @param filterMask      the filter mask
     * @param sortOrder       the sort order
     * @return a list of filtered and sorted audit logs
     */
    private List<AuditLogEntry> filterAndSort(List<AuditLogEntry> auditLogEntries, String filterMask, String sortOrder) {
        return sort(filter(auditLogEntries, filterMask), sortOrder);
    }
}
