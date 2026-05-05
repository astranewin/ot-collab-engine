package astranewin.dev.realtime_collaborative_editor;

import astranewin.dev.realtime_collaborative_editor.document.edit.OperationTransformer;
import astranewin.dev.realtime_collaborative_editor.document.edit.domain.Operation;
import astranewin.dev.realtime_collaborative_editor.document.edit.domain.OperationType;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.TimeUnit;

// Setup I used to get benchmark data
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@State(Scope.Benchmark)
public class OtBenchmark {
    private OperationTransformer transformer;
    private List<Operation> history;
    private Operation[] testPool;
    private int counter = 0;

    @Param({"50", "500", "5000", "50000"})
    public int historySize;

    @Setup(Level.Trial)
    public void setup() {
        transformer = new OperationTransformer();
        history = new ArrayList<>(historySize);
        Random random = new Random(42);

        for (int i = 0; i < historySize; i++) {
            OperationType type = random.nextBoolean() ? OperationType.INSERT : OperationType.DELETE;
            String text = type == OperationType.INSERT ? "char" + random.nextInt(100) : "";
            int pos = random.nextInt(Math.max(1, i));

            history.add(new Operation(null, "user1", type, pos, text, 0, i));
        }

        testPool = new Operation[100];
        for (int i = 0; i < 100; i++) {
            OperationType type = random.nextBoolean() ? OperationType.INSERT : OperationType.DELETE;
            int pos = random.nextInt(Math.max(1, historySize));
            testPool[i] = new Operation(null, "user2", type, pos, "new", 0, 0);
        }
    }

    @Benchmark
    public void testScaling(Blackhole blackhole) {
        Operation op = testPool[counter++ & 99];
        blackhole.consume(transformer.transformAgainst(history, op, 0));
    }

    public static void main(String[] args) throws Exception {
        Options opt = new OptionsBuilder()
                .include(OtBenchmark.class.getSimpleName())
                .forks(1)
                .warmupIterations(3)
                .measurementIterations(5)
                .build();

        new Runner(opt).run();
    }
}