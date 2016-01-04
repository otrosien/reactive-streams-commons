package reactivestreams.commons.publisher;

import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;
import reactivestreams.commons.subscriber.SubscriberDeferScalar;
import reactivestreams.commons.support.SubscriptionHelper;

import java.util.Objects;

/**
 * Emits a scalar value if the source sequence turns out to be empty.
 *
 * @param <T> the value type
 */
public final class PublisherDefaultIfEmpty<T> extends PublisherSource<T, T> {

    final T value;

    public PublisherDefaultIfEmpty(Publisher<? extends T> source, T value) {
        super(source);
        this.value = Objects.requireNonNull(value, "value");
    }

    @Override
    public void subscribe(Subscriber<? super T> s) {
        source.subscribe(new PublisherDefaultIfEmptySubscriber<>(s, value));
    }

    static final class PublisherDefaultIfEmptySubscriber<T>
      extends SubscriberDeferScalar<T, T>
    implements Upstream{

        final T value;

        Subscription s;

        boolean hasValue;

        public PublisherDefaultIfEmptySubscriber(Subscriber<? super T> actual, T value) {
            super(actual);
            this.value = value;
        }

        @Override
        public void request(long n) {
            super.request(n);
            s.request(n);
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
            }
        }

        @Override
        public void onNext(T t) {
            if (!hasValue) {
                hasValue = true;
            }

            subscriber.onNext(t);
        }

        @Override
        public void onComplete() {
            if (hasValue) {
                subscriber.onComplete();
            } else {
                set(value);
            }
        }

        @Override
        public T get() {
            return value;
        }

        @Override
        public void setValue(T value) {
            // value is constant
        }

        @Override
        public Object upstream() {
            return s;
        }

        @Override
        public Object delegateInput() {
            return value;
        }
    }
}