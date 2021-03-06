package reactivestreams.commons.publisher;

import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import org.junit.Assert;
import org.junit.Test;
import org.reactivestreams.Publisher;
import reactivestreams.commons.processor.SimpleProcessor;
import reactivestreams.commons.test.TestSubscriber;
import reactivestreams.commons.util.ConstructorTestBuilder;

public class PublisherWindowBoundaryAndSizeTest {

    @Test
    public void constructors() {
        ConstructorTestBuilder ctb = new ConstructorTestBuilder(PublisherWindowBoundaryAndSize.class);
        
        ctb.addRef("source", PublisherNever.instance());
        ctb.addRef("other", PublisherNever.instance());
        ctb.addRef("processorQueueSupplier", (Supplier<Queue<Object>>)() -> new ConcurrentLinkedQueue<>());
        ctb.addRef("drainQueueSupplier", (Supplier<Queue<Object>>)() -> new ConcurrentLinkedQueue<>());
        ctb.addInt("maxSize", 1, Integer.MAX_VALUE);
        
        ctb.test();
    }

    static <T> TestSubscriber<T> toList(Publisher<T> windows) {
        TestSubscriber<T> ts = new TestSubscriber<>();
        windows.subscribe(ts);
        return ts;
    }

    @SafeVarargs
    static <T> void expect(TestSubscriber<PublisherBase<T>> ts, int index, T... values) {
        toList(ts.values().get(index))
        .assertValues(values)
        .assertComplete()
        .assertNoError();
    }

    @SafeVarargs
    static <T> void awaitAndExpectValues(TestSubscriber<PublisherBase<T>> ts, int index, T... values) {
        TestSubscriber<T> tsi = toList(ts.values().get(index));
        tsi.await();
        tsi
        .awaitAndAssertValueCount(values.length)
        .assertValues(values)
        .assertNoError();
    }

    @Test
    public void normal() {
        TestSubscriber<PublisherBase<Integer>> ts = new TestSubscriber<>();
        
        SimpleProcessor<Integer> sp1 = new SimpleProcessor<>();
        SimpleProcessor<Integer> sp2 = new SimpleProcessor<>();
        
        sp1.window(sp2, 10).subscribe(ts);
        
        ts.assertValueCount(1);
        
        sp1.onNext(1);
        sp1.onNext(2);
        sp1.onNext(3);
        
        sp2.onNext(1);
        
        sp1.onNext(4);
        sp1.onNext(5);
        
        sp1.onComplete();
        
        ts.assertValueCount(2);

        expect(ts, 0, 1, 2, 3);
        expect(ts, 1, 4, 5);
        
        ts.assertNoError()
        .assertComplete();
        
        Assert.assertFalse("sp1 has subscribers", sp1.hasSubscribers());
        Assert.assertFalse("sp2 has subscribers", sp1.hasSubscribers());
    }
    
    @Test
    public void normalOverflow() {
        TestSubscriber<PublisherBase<Integer>> ts = new TestSubscriber<>();
        
        SimpleProcessor<Integer> sp1 = new SimpleProcessor<>();
        SimpleProcessor<Integer> sp2 = new SimpleProcessor<>();
        
        sp1.window(sp2, 3).subscribe(ts);

        ts.assertValueCount(1);

        sp1.onNext(1);
        sp1.onNext(2);
        sp1.onNext(3);

        ts.assertValueCount(2);
        
        sp1.onNext(4);
        sp1.onNext(5);
        
        sp2.onNext(100);

        ts.assertValueCount(3);
        
        sp1.onNext(6);
        sp1.onNext(7);
        sp1.onNext(8);

        ts.assertValueCount(4);
        
        sp2.onNext(200);

        ts.assertValueCount(5);
        
        sp1.onComplete();
        
        ts.assertValueCount(5);
        
        expect(ts, 0, 1, 2, 3);
        expect(ts, 1, 4, 5);
        expect(ts, 2, 6, 7, 8);
        expect(ts, 3);

        ts.assertNoError()
        .assertComplete();
        
        Assert.assertFalse("sp1 has subscribers", sp1.hasSubscribers());
        Assert.assertFalse("sp2 has subscribers", sp1.hasSubscribers());
    }

    @Test(timeout = 60000)
    public void concurrentWindowsLoop() {
        for (int i = 0; i < 100_000; i++) {
            concurrentWindows();
        }
    }
    
    @Test(timeout = 60000)
    public void concurrentWindows() {
        TestSubscriber<PublisherBase<Integer>> ts = new TestSubscriber<>();

        SimpleProcessor<Integer> sp1 = new SimpleProcessor<>();
        SimpleProcessor<Integer> sp2 = new SimpleProcessor<>();

        sp1
           .window(sp2.observeOn(ForkJoinPool.commonPool()), 3)
           .subscribe(ts);

        sp1.onNext(1);
        sp1.onNext(2);
        sp1.onNext(3);

        ts.awaitAndAssertValueCount(1);
        awaitAndExpectValues(ts, 0, 1, 2, 3);

        sp1.onNext(4);
        sp1.onNext(5);

        sp2.onNext(100);

        ts.awaitAndAssertValueCount(2);
        awaitAndExpectValues(ts, 1, 4, 5);

        sp1.onNext(6);
        sp1.onNext(7);
        sp1.onNext(8);

        sp2.onNext(200);

        ts.awaitAndAssertValueCount(4);
        awaitAndExpectValues(ts, 2, 6, 7, 8);
        awaitAndExpectValues(ts, 3);

        sp1.onNext(9);

        sp2.onNext(200);

        ts.awaitAndAssertValueCount(5);
        awaitAndExpectValues(ts, 4, 9);

        sp1.onComplete();

        ts.await();
        
        awaitAndExpectValues(ts, 5);

        ts.assertValueCount(6)
          .assertNoError()
          .assertComplete();

        Assert.assertFalse("sp1 has subscribers", sp1.hasSubscribers());
        Assert.assertFalse("sp2 has subscribers", sp1.hasSubscribers());
    }

    @Test
    public void normalMaxSize() {
        TestSubscriber<PublisherBase<Integer>> ts = new TestSubscriber<>();
        
        SimpleProcessor<Integer> sp1 = new SimpleProcessor<>();
        
        sp1.window(PublisherNever.instance(), 3).subscribe(ts);

        for (int i = 0; i < 99; i++) {
            sp1.onNext(i);
        }
        sp1.onComplete();
        
        for (int i = 0; i < 33; i++) {
            expect(ts, i, (i * 3), (i * 3 + 1), (i * 3 + 2));
        }
        expect(ts, 33);
        
        ts.assertNoError()
        .assertComplete();
        
        Assert.assertFalse("sp1 has subscribers", sp1.hasSubscribers());
        Assert.assertFalse("sp2 has subscribers", sp1.hasSubscribers());
    }

    @Test
    public void normalOtherCompletes() {
        TestSubscriber<PublisherBase<Integer>> ts = new TestSubscriber<>();
        
        SimpleProcessor<Integer> sp1 = new SimpleProcessor<>();
        SimpleProcessor<Integer> sp2 = new SimpleProcessor<>();
        
        sp1.window(sp2, 10).subscribe(ts);
        
        ts.assertValueCount(1);
        
        sp1.onNext(1);
        sp1.onNext(2);
        sp1.onNext(3);
        
        sp2.onNext(1);
        
        sp1.onNext(4);
        sp1.onNext(5);
        
        sp2.onComplete();
        
        ts.assertValueCount(2);

        expect(ts, 0, 1, 2, 3);
        expect(ts, 1, 4, 5);
        
        ts.assertNoError()
        .assertComplete();
        
        Assert.assertFalse("sp1 has subscribers", sp1.hasSubscribers());
        Assert.assertFalse("sp2 has subscribers", sp1.hasSubscribers());
    }

    @Test
    public void mainError() {
        TestSubscriber<PublisherBase<Integer>> ts = new TestSubscriber<>();
        
        SimpleProcessor<Integer> sp1 = new SimpleProcessor<>();
        SimpleProcessor<Integer> sp2 = new SimpleProcessor<>();
        
        sp1.window(sp2, 10).subscribe(ts);
        
        ts.assertValueCount(1);
        
        sp1.onNext(1);
        sp1.onNext(2);
        sp1.onNext(3);
        
        sp2.onNext(1);
        
        sp1.onNext(4);
        sp1.onNext(5);
        
        sp1.onError(new RuntimeException("forced failure"));
        
        ts.assertValueCount(2);

        expect(ts, 0, 1, 2, 3);
        
        toList(ts.values().get(1))
        .assertValues(4, 5)
        .assertError(RuntimeException.class)
        .assertErrorMessage("forced failure")
        .assertNotComplete();
        
        ts
        .assertError(RuntimeException.class)
        .assertErrorMessage("forced failure")
        .assertNotComplete();
        
        Assert.assertFalse("sp1 has subscribers", sp1.hasSubscribers());
        Assert.assertFalse("sp2 has subscribers", sp1.hasSubscribers());
    }

    @Test
    public void otherError() {
        TestSubscriber<PublisherBase<Integer>> ts = new TestSubscriber<>();
        
        SimpleProcessor<Integer> sp1 = new SimpleProcessor<>();
        SimpleProcessor<Integer> sp2 = new SimpleProcessor<>();
        
        sp1.window(sp2, 10).subscribe(ts);
        
        ts.assertValueCount(1);
        
        sp1.onNext(1);
        sp1.onNext(2);
        sp1.onNext(3);
        
        sp2.onNext(1);
        
        sp1.onNext(4);
        sp1.onNext(5);
        
        sp2.onError(new RuntimeException("forced failure"));
        
        ts.assertValueCount(2);

        expect(ts, 0, 1, 2, 3);
        
        toList(ts.values().get(1))
        .assertValues(4, 5)
        .assertError(RuntimeException.class)
        .assertErrorMessage("forced failure")
        .assertNotComplete();
        
        ts
        .assertError(RuntimeException.class)
        .assertErrorMessage("forced failure")
        .assertNotComplete();
        
        Assert.assertFalse("sp1 has subscribers", sp1.hasSubscribers());
        Assert.assertFalse("sp2 has subscribers", sp1.hasSubscribers());
    }

    @Test(timeout = 60000)
    public void asyncConsumers() {
        for (int maxSize = 1; maxSize < 12; maxSize++) {
//            System.out.println("asyncConsumers >> " + maxSize);
            ScheduledExecutorService exec = Executors.newScheduledThreadPool(3);

            try {
                List<TestSubscriber<Long>> tss = new ArrayList<>();

                TestSubscriber<PublisherBase<Long>> ts = new TestSubscriber<PublisherBase<Long>>() {
                    @Override
                    public void onNext(PublisherBase<Long> t) {
                        TestSubscriber<Long> its = new  TestSubscriber<>();
                        tss.add(its);
                        t.observeOn(ForkJoinPool.commonPool()).subscribe(its);
                    }
                };

                PublisherBase.interval(1, TimeUnit.MILLISECONDS, exec).observeOn(exec).take(2500)
                .window(PublisherBase.interval(5, TimeUnit.MILLISECONDS, exec), maxSize)
                .subscribe(ts);

                ts.await(5, TimeUnit.SECONDS);
                List<Long> data = new ArrayList<>(2500);
                for (TestSubscriber<Long> its : tss) {
                    its.await(5, TimeUnit.SECONDS);
                    data.addAll(its.values());
                }

                Assert.assertTrue(data.size() == 2500);
                for (int i = 0; i < data.size() - 1; i++) {
                    Long a1 = data.get(i);
                    Long a2 = data.get(i + 1);

                    Assert.assertEquals((long)a2, a1 + 1);
                }
            } finally {
                exec.shutdownNow();
            }
        }
    }

    void block(Long n){
        try{
            Thread.sleep(10);
        }
        catch (InterruptedException ie){
            //IGNORE
        }
    }
}
