package reactivestreams.commons.publisher;

import java.util.Arrays;
import java.util.Iterator;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLongFieldUpdater;

import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;
import reactivestreams.commons.flow.Fuseable;
import reactivestreams.commons.flow.MultiReceiver;
import reactivestreams.commons.flow.Producer;
import reactivestreams.commons.state.Cancellable;
import reactivestreams.commons.state.Requestable;
import reactivestreams.commons.util.BackpressureHelper;
import reactivestreams.commons.util.EmptySubscription;
import reactivestreams.commons.util.SubscriptionHelper;

/**
 * Emits the contents of a wrapped (shared) array.
 *
 * @param <T> the value type
 */
public final class PublisherArray<T> 
extends PublisherBase<T>
        implements Fuseable {
    final T[] array;

    @SafeVarargs
    public PublisherArray(T... array) {
        this.array = Objects.requireNonNull(array, "array");
    }

    @Override
    public void subscribe(Subscriber<? super T> s) {
        if (array.length == 0) {
            EmptySubscription.complete(s);
            return;
        }
        s.onSubscribe(new ArraySubscription<>(s, array));
    }

    static final class ArraySubscription<T>
      implements Producer, Requestable, Cancellable, MultiReceiver, SynchronousSubscription<T> {
        final Subscriber<? super T> actual;

        final T[] array;

        int index;

        volatile boolean cancelled;

        volatile long requested;
        @SuppressWarnings("rawtypes")
        static final AtomicLongFieldUpdater<ArraySubscription> REQUESTED =
          AtomicLongFieldUpdater.newUpdater(ArraySubscription.class, "requested");

        public ArraySubscription(Subscriber<? super T> actual, T[] array) {
            this.actual = actual;
            this.array = array;
        }

        @Override
        public void request(long n) {
            if (SubscriptionHelper.validate(n)) {
                if (BackpressureHelper.addAndGet(REQUESTED, this, n) == 0) {
                    if (n == Long.MAX_VALUE) {
                        fastPath();
                    } else {
                        slowPath(n);
                    }
                }
            }
        }

        void slowPath(long n) {
            final T[] a = array;
            final int len = a.length;
            final Subscriber<? super T> s = actual;

            int i = index;
            int e = 0;

            for (; ; ) {
                if (cancelled) {
                    return;
                }

                while (i != len && e != n) {
                    T t = a[i];

                    if (t == null) {
                        s.onError(new NullPointerException("The " + i + "th array element was null"));
                        return;
                    }

                    s.onNext(t);

                    if (cancelled) {
                        return;
                    }

                    i++;
                    e++;
                }

                if (i == len) {
                    s.onComplete();
                    return;
                }

                n = requested;

                if (n == e) {
                    index = i;
                    n = REQUESTED.addAndGet(this, -e);
                    if (n == 0) {
                        return;
                    }
                    e = 0;
                }
            }
        }

        void fastPath() {
            final T[] a = array;
            final int len = a.length;
            final Subscriber<? super T> s = actual;

            for (int i = index; i != len; i++) {
                if (cancelled) {
                    return;
                }

                T t = a[i];

                if (t == null) {
                    s.onError(new NullPointerException("The " + i + "th array element was null"));
                    return;
                }

                s.onNext(t);
            }
            if (cancelled) {
                return;
            }
            s.onComplete();
        }

        @Override
        public void cancel() {
            cancelled = true;
        }

        @Override
        public boolean isCancelled() {
            return cancelled;
        }

        @Override
        public Object downstream() {
            return actual;
        }

        @Override
        public long requestedFromDownstream() {
            return requested;
        }

        @Override
        public Iterator<?> upstreams() {
            return array instanceof Publisher[] ? Arrays.asList(array).iterator() : null;
        }

        @Override
        public long upstreamCount() {
            return array instanceof Publisher[] ? array.length : -1;
        }

        @Override
        public T poll() {
            int i = index;
            T[] a = array;
            if (i != a.length) {
                T t = a[i];
                if (t == null) {
                    throw new NullPointerException();
                }
                index = i + 1;
                return t;
            }
            return null;
        }

        @Override
        public T peek() {
            int i = index;
            T[] a = array;
            if (i < a.length) {
                T t = a[i];
                if (t == null) {
                    throw new NullPointerException();
                }
                return t;
            }
            return null;
        }

        @Override
        public boolean isEmpty() {
            return index == array.length;
        }

        @Override
        public void clear() {
            index = array.length;
        }
        
        @Override
        public void drop() {
            index++;
        }
        
        @Override
        public int size() {
            return array.length - index;
        }
    }

}
