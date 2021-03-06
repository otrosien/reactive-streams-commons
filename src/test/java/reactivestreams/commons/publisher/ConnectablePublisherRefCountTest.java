package reactivestreams.commons.publisher;

import org.junit.*;

import reactivestreams.commons.processor.SimpleProcessor;
import reactivestreams.commons.test.TestSubscriber;
import reactivestreams.commons.util.ConstructorTestBuilder;

public class ConnectablePublisherRefCountTest {
    @Test
    public void constructors() {
        ConstructorTestBuilder ctb = new ConstructorTestBuilder(ConnectablePublisherRefCount.class);
        
        ctb.addRef("source", PublisherNever.instance().publish());
        ctb.addInt("n", 1, Integer.MAX_VALUE);
        
        ctb.test();
    }
    
    @Test
    public void normal() {
        SimpleProcessor<Integer> sp = new SimpleProcessor<>();
        
        PublisherBase<Integer> p = sp.publish().refCount();
        
        Assert.assertFalse("sp has subscribers?", sp.hasSubscribers());
        
        TestSubscriber<Integer> ts1 = new TestSubscriber<>();
        p.subscribe(ts1);

        Assert.assertTrue("sp has no subscribers?", sp.hasSubscribers());

        TestSubscriber<Integer> ts2 = new TestSubscriber<>();
        p.subscribe(ts2);

        Assert.assertTrue("sp has no subscribers?", sp.hasSubscribers());
        
        sp.onNext(1);
        sp.onNext(2);
        
        ts1.cancel();
        
        Assert.assertTrue("sp has no subscribers?", sp.hasSubscribers());
        
        sp.onNext(3);
        
        ts2.cancel();
        
        Assert.assertFalse("sp has subscribers?", sp.hasSubscribers());
        
        ts1.assertValues(1, 2)
        .assertNoError()
        .assertNotComplete();

        ts2.assertValues(1, 2, 3)
        .assertNoError()
        .assertNotComplete();
    }

    @Test
    public void normalTwoSubscribers() {
        SimpleProcessor<Integer> sp = new SimpleProcessor<>();
        
        PublisherBase<Integer> p = sp.publish().refCount(2);
        
        Assert.assertFalse("sp has subscribers?", sp.hasSubscribers());
        
        TestSubscriber<Integer> ts1 = new TestSubscriber<>();
        p.subscribe(ts1);

        Assert.assertFalse("sp has subscribers?", sp.hasSubscribers());

        TestSubscriber<Integer> ts2 = new TestSubscriber<>();
        p.subscribe(ts2);

        Assert.assertTrue("sp has no subscribers?", sp.hasSubscribers());
        
        sp.onNext(1);
        sp.onNext(2);
        
        ts1.cancel();
        
        Assert.assertTrue("sp has no subscribers?", sp.hasSubscribers());
        
        sp.onNext(3);
        
        ts2.cancel();
        
        Assert.assertFalse("sp has subscribers?", sp.hasSubscribers());
        
        ts1.assertValues(1, 2)
        .assertNoError()
        .assertNotComplete();

        ts2.assertValues(1, 2, 3)
        .assertNoError()
        .assertNotComplete();
    }

    @Test
    public void upstreamCompletes() {
        
        PublisherBase<Integer> p = PublisherBase.range(1, 5).publish().refCount();

        TestSubscriber<Integer> ts1 = new TestSubscriber<>();
        p.subscribe(ts1);

        ts1.assertValues(1, 2, 3, 4, 5)
        .assertNoError()
        .assertComplete();

        TestSubscriber<Integer> ts2 = new TestSubscriber<>();
        p.subscribe(ts2);

        ts2.assertValues(1, 2, 3, 4, 5)
        .assertNoError()
        .assertComplete();

    }

    @Test
    public void upstreamCompletesTwoSubscribers() {
        
        PublisherBase<Integer> p = PublisherBase.range(1, 5).publish().refCount(2);

        TestSubscriber<Integer> ts1 = new TestSubscriber<>();
        p.subscribe(ts1);

        ts1.assertNoEvents();

        TestSubscriber<Integer> ts2 = new TestSubscriber<>();
        p.subscribe(ts2);

        ts1.assertResult(1, 2, 3, 4, 5);
        ts2.assertResult(1, 2, 3, 4, 5);
    }

    @Test
    public void subscribersComeAndGoBelowThreshold() {
        PublisherBase<Integer> p = PublisherBase.range(1, 5).publish().refCount(2);

        Runnable r = p.subscribe();
        r.run();
        p.subscribe().run();
        p.subscribe().run();
        p.subscribe().run();
        p.subscribe().run();
        
        TestSubscriber<Integer> ts1 = new TestSubscriber<>();
        p.subscribe(ts1);

        ts1.assertNoEvents();

        TestSubscriber<Integer> ts2 = new TestSubscriber<>();
        p.subscribe(ts2);

        ts1.assertResult(1, 2, 3, 4, 5);
        ts2.assertResult(1, 2, 3, 4, 5);
    }
}
