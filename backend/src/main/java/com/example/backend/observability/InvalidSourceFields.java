package com.example.backend.observability;

import com.example.backend.observability.LogEvent.Category;
import com.example.backend.observability.LogEvent.Operation;
import com.example.backend.observability.LogEvent.Type;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * How a read that shows the in-house patient source's data as received (ADR 0020) reports the
 * fields of an answer whose format was wrong, which the SPA shows as "Error": one {@code WARN},
 * {@code patient_source.invalid_fields}, for the whole answer, naming the read ({@link #READ}) and
 * each field by its path in the answer ({@link #INVALID_FIELDS}) — never its value or the patient
 * (ADR 0003) — and the {@value #COUNTER} counter, one count for each such field, tagged by the
 * {@code read} and the {@code field}, both from closed sets: the reads, and the elements each
 * record declares. An answer with none reports nothing.
 *
 * <p>Here rather than in the patient module, which writes no log record (ADR 0014): the adapter
 * names the fields, and this records them.
 */
public final class InvalidSourceFields {

    /** The counter's name. */
    public static final String COUNTER = "patient_source.invalid_fields";

    /**
     * Which read of the source the record is about, as the counter's {@code read} tag spells it
     * ({@link SourceRead#tag()}): {@code ffi} or {@code numc}. The schema has no field for it, so it is namespaced under {@code app.}.
     */
    public static final String READ = "app.patient_source.read";

    /**
     * The path of each field of the answer whose format was wrong, in the answer's order —
     * {@code entry[3].issueDate} — never its value. Namespaced as {@link #READ} is.
     */
    public static final String INVALID_FIELDS = "app.patient_source.invalid_fields";

    private static final Logger log = LoggerFactory.getLogger(InvalidSourceFields.class);

    /**
     * One field of an answer whose format was wrong.
     *
     * @param path its path in the answer, {@code entry[3].issueDate}
     * @param name the element it is, as its record declares it: {@code issueDate}
     */
    public record Field(String path, String name) {
    }

    private final MeterRegistry meters;

    public InvalidSourceFields(MeterRegistry meters) {
        this.meters = meters;
    }

    /** Reports {@code fields}, the invalid fields of an answer of the read {@code read}, in order. */
    public void report(SourceRead read, List<Field> fields) {
        if (fields.isEmpty()) {
            return;
        }
        for (Field field : fields) {
            counter(read, field.name()).increment();
        }
        LogEvent.successAtWarn(log, Operation.PATIENT_SOURCE_INVALID_FIELDS, Category.NETWORK,
                        Type.INFO)
                .addKeyValue(READ, read.tag())
                .addKeyValue(INVALID_FIELDS, fields.stream().map(Field::path).toList())
                .log();
    }

    private Counter counter(SourceRead read, String field) {
        // The registry hands back the one meter per read and field, registering it once.
        return Counter.builder(COUNTER)
                .description("Fields of the patient source's answers whose format was wrong, by"
                        + " read and field")
                .tag("read", read.tag())
                .tag("field", field)
                .register(meters);
    }
}
