package reactivestreams.commons.publisher;

import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;
import reactivestreams.commons.flow.Receiver;
import reactivestreams.commons.subscriber.DeferredScalarSubscriber;
import reactivestreams.commons.util.SubscriptionHelper;

public final class PublisherHasElements<T> extends PublisherSource<T, Boolean> {

    public PublisherHasElements(Publisher<? extends T> source) {
        super(source);
    }

    @Override
    public void subscribe(Subscriber<? super Boolean> s) {
        source.subscribe(new PublisherHasElementsSubscriber<>(s));
    }

    static final class PublisherHasElementsSubscriber<T> extends DeferredScalarSubscriber<T, Boolean>
            implements Receiver {
        Subscription s;

        public PublisherHasElementsSubscriber(Subscriber<? super Boolean> actual) {
            super(actual);
        }

        @Override
        public void cancel() {
            super.cancel();
            s.cancel();
        }

        @Override
        public void onSubscribe(Subscription s) {
            if (SubscriptionHelper.validate(this.s, s)) {
                this.s = s;
                subscriber.onSubscribe(this);

                s.request(Long.MAX_VALUE);
            }
        }

        @Override
        public void onNext(T t) {
            s.cancel();

            complete(true);
        }

        @Override
        public void onComplete() {
            complete(false);
        }

        @Override
        public Object upstream() {
            return s;
        }
    }
}
