package reactivestreams.commons.publisher;

import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;
import org.reactivestreams.Publisher;

import reactivestreams.commons.publisher.internal.*;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;


/**
 * Example benchmark. Run from command line as
 * <br>
 * gradle jmh -Pjmh='PublisherIterablePerf'
 */
@BenchmarkMode(Mode.Throughput)
@Warmup(iterations = 5)
@Measurement(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@OutputTimeUnit(TimeUnit.SECONDS)
@Fork(value = 1)
@State(Scope.Thread)
public class PublisherIterablePerf {

    @Param({"1", "1000", "1000000"})
    int count;

    List<Integer> list;

    Publisher<Integer> source;

    PerfSubscriber         sharedSubscriber1;
    PerfSlowPathSubscriber sharedSubscriber2;

    @Setup
    public void setup(Blackhole bh) {
        Integer[] a = new Integer[count];
        for (int i = 0; i < count; i++) {
            a[i] = 777;
        }
        list = Arrays.asList(a);
        source = createSource();
        sharedSubscriber1 = new PerfSubscriber(bh);
        sharedSubscriber2 = new PerfSlowPathSubscriber(bh, count);
    }

    Publisher<Integer> createSource() {
        return new PublisherIterable<>(list);
    }

    @Benchmark
    public void standard1(Blackhole bh) {
        source.subscribe(new PerfSubscriber(bh));
    }

    @Benchmark
    public void shared1() {
        source.subscribe(sharedSubscriber1);
    }

    @Benchmark
    public void createNew1(Blackhole bh) {
        Publisher<Integer> p = createSource();
        bh.consume(p);
        p.subscribe(new PerfSubscriber(bh));
    }

    @Benchmark
    public void standard2(Blackhole bh) {
        source.subscribe(new PerfSlowPathSubscriber(bh, count));
    }

    @Benchmark
    public void shared2() {
        source.subscribe(sharedSubscriber2);
    }

    @Benchmark
    public void createNew2(Blackhole bh) {
        Publisher<Integer> p = createSource();
        bh.consume(p);
        p.subscribe(new PerfSlowPathSubscriber(bh, count));
    }
}
