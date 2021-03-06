/*
 * Copyright (c) 2011-2016 Pivotal Software Inc, All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package reactivestreams.commons.publisher;

import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;

import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;
import reactivestreams.commons.flow.Producer;
import reactivestreams.commons.flow.Receiver;
import reactivestreams.commons.state.Backpressurable;
import reactivestreams.commons.state.Completable;
import reactivestreams.commons.state.Prefetchable;
import reactivestreams.commons.util.SubscriptionHelper;
import reactivestreams.commons.util.UnsignalledExceptions;

/**
 * Takes only the first N values from the source Publisher.
 * <p>
 * If N is zero, the subscriber gets completed if the source completes, signals an error or
 * signals its first value (which is not not relayed though).
 *
 * @param <T> the value type
 */
public final class PublisherTake<T> extends PublisherSource<T, T> {

    final long n;

    public PublisherTake(Publisher<? extends T> source, long n) {
        super(source);
        if (n < 0) {
            throw new IllegalArgumentException("n >= 0 required but it was " + n);
        }
        this.n = n;
    }

    public Publisher<? extends T> source() {
        return source;
    }

    public long n() {
        return n;
    }

    @Override
    public void subscribe(Subscriber<? super T> s) {
        source.subscribe(new PublisherTakeSubscriber<>(s, n));
    }

    @Override
    public long getCapacity() {
        return n;
    }

    static final class PublisherTakeSubscriber<T>
      implements Subscriber<T>, Subscription, Completable, Receiver, Prefetchable, Backpressurable, Producer {

        final Subscriber<? super T> actual;

        final long n;

        long remaining;

        Subscription s;

        boolean done;

        volatile int wip;
        @SuppressWarnings("rawtypes")
        static final AtomicIntegerFieldUpdater<PublisherTakeSubscriber> WIP =
          AtomicIntegerFieldUpdater.newUpdater(PublisherTakeSubscriber.class, "wip");

        public PublisherTakeSubscriber(Subscriber<? super T> actual, long n) {
            this.actual = actual;
            this.n = n;
            this.remaining = n;
        }

        @Override
        public void onSubscribe(Subscription s) {
            if (SubscriptionHelper.validate(this.s, s)) {
                this.s = s;
                actual.onSubscribe(this);
                if (n == 0 && wip == 0) {
                    request(Long.MAX_VALUE);
                }
            }
        }

        @Override
        public void onNext(T t) {
            if (done) {
                UnsignalledExceptions.onNextDropped(t);
                return;
            }

            long r = remaining;

            if (r == 0) {
                onComplete();
                return;
            }

            remaining = --r;
            boolean stop = r == 0L;

            actual.onNext(t);

            if (stop) {
                s.cancel();

                onComplete();
            }
        }

        @Override
        public void onError(Throwable t) {
            if (done) {
                UnsignalledExceptions.onErrorDropped(t);
                return;
            }
            done = true;
            actual.onError(t);
        }

        @Override
        public void onComplete() {
            if (done) {
                return;
            }
            done = true;
            actual.onComplete();
        }

        @Override
        public void request(long n) {
            if (wip != 0) {
                s.request(n);
            } else if (WIP.compareAndSet(this, 0, 1)) {
                if (n >= this.n) {
                    s.request(Long.MAX_VALUE);
                } else {
                    s.request(n);
                }
            }
        }

        @Override
        public void cancel() {
            s.cancel();
        }
        @Override
        public boolean isStarted() {
            return s != null && !done;
        }

        @Override
        public boolean isTerminated() {
            return done;
        }

        @Override
        public long getCapacity() {
            return n;
        }

        @Override
        public Object upstream() {
            return s;
        }

        @Override
        public long expectedFromUpstream() {
            return remaining;
        }

        @Override
        public Object downstream() {
            return actual;
        }

        @Override
        public long limit() {
            return 0;
        }
    }
}
