package reactivestreams.commons.publisher;

import java.util.function.Consumer;
import java.util.function.LongConsumer;

import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;
import reactivestreams.commons.flow.Fuseable;
import reactivestreams.commons.flow.Producer;
import reactivestreams.commons.flow.Receiver;
import reactivestreams.commons.util.EmptySubscription;
import reactivestreams.commons.util.ExceptionHelper;

/**
 * Peek into the lifecycle events and signals of a sequence.
 * <p>
 * <p>
 * The callbacks are all optional.
 * <p>
 * <p>
 * Crashes by the lambdas are ignored.
 *
 * @param <T> the value type
 */
public final class PublisherPeekFuseable<T> extends PublisherSource<T, T> implements Fuseable, PublisherPeekHelper<T> {

    final Consumer<? super Subscription> onSubscribeCall;

    final Consumer<? super T> onNextCall;

    final Consumer<? super Throwable> onErrorCall;

    final Runnable onCompleteCall;

    final Runnable onAfterTerminateCall;

    final LongConsumer onRequestCall;

    final Runnable onCancelCall;

    public PublisherPeekFuseable(Publisher<? extends T> source, Consumer<? super Subscription> onSubscribeCall,
                         Consumer<? super T> onNextCall, Consumer<? super Throwable> onErrorCall, Runnable
                           onCompleteCall,
                         Runnable onAfterTerminateCall, LongConsumer onRequestCall, Runnable onCancelCall) {
        super(source);
        if (!(source instanceof Fuseable)) {
            throw new IllegalArgumentException("The source must implement the Fuseable interface for this operator to work");
        }
        
        this.onSubscribeCall = onSubscribeCall;
        this.onNextCall = onNextCall;
        this.onErrorCall = onErrorCall;
        this.onCompleteCall = onCompleteCall;
        this.onAfterTerminateCall = onAfterTerminateCall;
        this.onRequestCall = onRequestCall;
        this.onCancelCall = onCancelCall;
    }

    @Override
    public void subscribe(Subscriber<? super T> s) {
        if (s instanceof ConditionalSubscriber) {
            source.subscribe(new PublisherPeekFuseableConditionalSubscriber<>((ConditionalSubscriber<? super T>)s, this));
            return;
        }
        source.subscribe(new PublisherPeekFuseableSubscriber<>(s, this));
    }

    static final class PublisherPeekFuseableSubscriber<T> 
    implements Subscriber<T>, Receiver, Producer, SynchronousSubscription<T> {

        final Subscriber<? super T> actual;

        final PublisherPeekHelper<T> parent;

        QueueSubscription<T> s;

        int sourceMode;

        public PublisherPeekFuseableSubscriber(Subscriber<? super T> actual, PublisherPeekHelper<T> parent) {
            this.actual = actual;
            this.parent = parent;
        }

        @Override
        public void request(long n) {
            if(parent.onRequestCall() != null) {
                try {
                    parent.onRequestCall().accept(n);
                }
                catch (Throwable e) {
                    cancel();
                    onError(ExceptionHelper.unwrap(e));
                    return;
                }
            }
            s.request(n);
        }

        @Override
        public void cancel() {
            if(parent.onCancelCall() != null) {
                try {
                    parent.onCancelCall().run();
                }
                catch (Throwable e) {
                    ExceptionHelper.throwIfFatal(e);
                    s.cancel();
                    onError(ExceptionHelper.unwrap(e));
                    return;
                }
            }
            s.cancel();
        }

        @SuppressWarnings("unchecked")
        @Override
        public void onSubscribe(Subscription s) {
            if(parent.onSubscribeCall() != null) {
                try {
                    parent.onSubscribeCall().accept(s);
                }
                catch (Throwable e) {
                    s.cancel();
                    actual.onSubscribe(EmptySubscription.INSTANCE);
                    onError(e);
                    return;
                }
            }
            this.s = (QueueSubscription<T>)s;
            actual.onSubscribe(this);
        }

        @Override
        public void onNext(T t) {
            if (sourceMode == NONE) {
                if (parent.onNextCall() != null) {
                    try {
                        parent.onNextCall().accept(t);
                    }
                    catch (Throwable e) {
                        cancel();
                        ExceptionHelper.throwIfFatal(e);
                        onError(ExceptionHelper.unwrap(e));
                        return;
                    }
                }
                actual.onNext(t);
            } else 
            if (sourceMode == ASYNC) {
                actual.onNext(null);
            }
        }

        @Override
        public void onError(Throwable t) {
            if(parent.onErrorCall() != null) {
                ExceptionHelper.throwIfFatal(t);
                parent.onErrorCall().accept(t);
            }

            actual.onError(t);

            if(parent.onAfterTerminateCall() != null) {
                try {
                    parent.onAfterTerminateCall().run();
                }
                catch (Throwable e) {
                    ExceptionHelper.throwIfFatal(e);
                    Throwable _e = ExceptionHelper.unwrap(e);
                    e.addSuppressed(ExceptionHelper.unwrap(t));
                    if(parent.onErrorCall() != null) {
                        parent.onErrorCall().accept(_e);
                    }
                    actual.onError(_e);
                }
            }
        }

        @Override
        public void onComplete() {
            if(parent.onCompleteCall() != null) {
                try {
                    parent.onCompleteCall().run();
                }
                catch (Throwable e) {
                    ExceptionHelper.throwIfFatal(e);
                    onError(ExceptionHelper.unwrap(e));
                    return;
                }
            }

            actual.onComplete();

            if(parent.onAfterTerminateCall() != null) {
                try {
                    parent.onAfterTerminateCall().run();
                }
                catch (Throwable e) {
                    ExceptionHelper.throwIfFatal(e);
                    Throwable _e = ExceptionHelper.unwrap(e);
                    if(parent.onErrorCall() != null) {
                        parent.onErrorCall().accept(_e);
                    }
                    actual.onError(_e);
                }
            }
        }

        @Override
        public Object downstream() {
            return actual;
        }

        @Override
        public Object upstream() {
            return s;
        }
        
        @Override
        public T poll() {
            T v = s.poll();
            if (v != null && parent.onNextCall() != null) {
                parent.onNextCall().accept(v);
            }
            if (v == null && sourceMode == SYNC) {
                Runnable call = parent.onCompleteCall();
                if (call != null) {
                    call.run();
                }
                call = parent.onAfterTerminateCall();
                if (call != null) {
                    call.run();
                }
            }
            return v;
        }

        @Override
        public T peek() {
            T v = s.peek();
            if (v != null && parent.onNextCall() != null) {
                parent.onNextCall().accept(v);
            }
            if (v == null && sourceMode == SYNC) {
                Runnable call = parent.onCompleteCall();
                if (call != null) {
                    call.run();
                }
                call = parent.onAfterTerminateCall();
                if (call != null) {
                    call.run();
                }
            }
            return v;
        }

        @Override
        public boolean isEmpty() {
            return s.isEmpty();
        }

        @Override
        public void clear() {
            s.clear();
        }

        @Override
        public int requestFusion(int requestedMode) {
            int m;
            if ((requestedMode & Fuseable.THREAD_BARRIER) != 0) {
                if ((requestedMode & Fuseable.SYNC) != 0) {
                    m = s.requestFusion(Fuseable.SYNC);
                } else {
                    m = Fuseable.NONE;
                }
            } else {
                m = s.requestFusion(requestedMode);
            }
            sourceMode = m;
            return m;
        }

        @Override
        public void drop() {
            s.drop();
        }
        
        @Override
        public int size() {
            return s.size();
        }
    }

    static final class PublisherPeekFuseableConditionalSubscriber<T> 
    implements ConditionalSubscriber<T>, Receiver, Producer, SynchronousSubscription<T> {

        final ConditionalSubscriber<? super T> actual;

        final PublisherPeekHelper<T> parent;

        QueueSubscription<T> s;

        int sourceMode;

        public PublisherPeekFuseableConditionalSubscriber(ConditionalSubscriber<? super T> actual, PublisherPeekHelper<T> parent) {
            this.actual = actual;
            this.parent = parent;
        }

        @Override
        public void request(long n) {
            if(parent.onRequestCall() != null) {
                try {
                    parent.onRequestCall().accept(n);
                }
                catch (Throwable e) {
                    cancel();
                    onError(ExceptionHelper.unwrap(e));
                    return;
                }
            }
            s.request(n);
        }

        @Override
        public void cancel() {
            if(parent.onCancelCall() != null) {
                try {
                    parent.onCancelCall().run();
                }
                catch (Throwable e) {
                    ExceptionHelper.throwIfFatal(e);
                    s.cancel();
                    onError(ExceptionHelper.unwrap(e));
                    return;
                }
            }
            s.cancel();
        }

        @SuppressWarnings("unchecked")
        @Override
        public void onSubscribe(Subscription s) {
            if(parent.onSubscribeCall() != null) {
                try {
                    parent.onSubscribeCall().accept(s);
                }
                catch (Throwable e) {
                    s.cancel();
                    actual.onSubscribe(EmptySubscription.INSTANCE);
                    onError(e);
                    return;
                }
            }
            this.s = (QueueSubscription<T>)s;
            actual.onSubscribe(this);
        }

        @Override
        public void onNext(T t) {
            if (sourceMode == NONE) {
                if (parent.onNextCall() != null) {
                    try {
                        parent.onNextCall().accept(t);
                    }
                    catch (Throwable e) {
                        cancel();
                        ExceptionHelper.throwIfFatal(e);
                        onError(ExceptionHelper.unwrap(e));
                        return;
                    }
                }
                actual.onNext(t);
            } else 
            if (sourceMode == ASYNC) {
                actual.onNext(null);
            }
        }
        
        @Override
        public boolean tryOnNext(T t) {
            if (sourceMode == NONE) {
                if (parent.onNextCall() != null) {
                    try {
                        parent.onNextCall().accept(t);
                    }
                    catch (Throwable e) {
                        cancel();
                        ExceptionHelper.throwIfFatal(e);
                        onError(ExceptionHelper.unwrap(e));
                        return true;
                    }
                }
                return actual.tryOnNext(t);
            } else 
            if (sourceMode == ASYNC) {
                actual.onNext(null);
            }
            return true;
        }
        

        @Override
        public void onError(Throwable t) {
            if(parent.onErrorCall() != null) {
                ExceptionHelper.throwIfFatal(t);
                parent.onErrorCall().accept(t);
            }

            actual.onError(t);

            if(parent.onAfterTerminateCall() != null) {
                try {
                    parent.onAfterTerminateCall().run();
                }
                catch (Throwable e) {
                    ExceptionHelper.throwIfFatal(e);
                    Throwable _e = ExceptionHelper.unwrap(e);
                    e.addSuppressed(ExceptionHelper.unwrap(t));
                    if(parent.onErrorCall() != null) {
                        parent.onErrorCall().accept(_e);
                    }
                    actual.onError(_e);
                }
            }
        }

        @Override
        public void onComplete() {
            if(parent.onCompleteCall() != null) {
                try {
                    parent.onCompleteCall().run();
                }
                catch (Throwable e) {
                    ExceptionHelper.throwIfFatal(e);
                    onError(ExceptionHelper.unwrap(e));
                    return;
                }
            }

            actual.onComplete();

            if(parent.onAfterTerminateCall() != null) {
                try {
                    parent.onAfterTerminateCall().run();
                }
                catch (Throwable e) {
                    ExceptionHelper.throwIfFatal(e);
                    Throwable _e = ExceptionHelper.unwrap(e);
                    if(parent.onErrorCall() != null) {
                        parent.onErrorCall().accept(_e);
                    }
                    actual.onError(_e);
                }
            }
        }

        @Override
        public Object downstream() {
            return actual;
        }

        @Override
        public Object upstream() {
            return s;
        }
        
        @Override
        public T poll() {
            T v = s.poll();
            if (v != null && parent.onNextCall() != null) {
                parent.onNextCall().accept(v);
            }
            if (v == null && sourceMode == SYNC) {
                parent.onCompleteCall().run();
                parent.onAfterTerminateCall().run();
            }
            return v;
        }

        @Override
        public T peek() {
            T v = s.peek();
            if (v != null && parent.onNextCall() != null) {
                parent.onNextCall().accept(v);
            }
            if (v == null && sourceMode == SYNC) {
                parent.onCompleteCall().run();
                parent.onAfterTerminateCall().run();
            }
            return v;
        }

        @Override
        public boolean isEmpty() {
            return s.isEmpty();
        }

        @Override
        public void clear() {
            s.clear();
        }

        @Override
        public int requestFusion(int requestedMode) {
            int m = s.requestFusion(requestedMode);
            if (m != Fuseable.NONE) {
                sourceMode = m == Fuseable.SYNC ? SYNC : ((requestedMode & Fuseable.THREAD_BARRIER) != 0 ? NONE : ASYNC);
            }
            return m;
        }

        @Override
        public void drop() {
            s.drop();
        }
        
        @Override
        public int size() {
            return s.size();
        }
    }

    @Override
    public Consumer<? super Subscription> onSubscribeCall() {
        return onSubscribeCall;
    }

    @Override
    public Consumer<? super T> onNextCall() {
        return onNextCall;
    }

    @Override
    public Consumer<? super Throwable> onErrorCall() {
        return onErrorCall;
    }

    @Override
    public Runnable onCompleteCall() {
        return onCompleteCall;
    }

    @Override
    public Runnable onAfterTerminateCall() {
        return onAfterTerminateCall;
    }

    @Override
    public LongConsumer onRequestCall() {
        return onRequestCall;
    }

    @Override
    public Runnable onCancelCall() {
        return onCancelCall;
    }

    static final class PublisherPeekConditionalSubscriber<T> implements ConditionalSubscriber<T>, Subscription, Receiver, Producer {

        final ConditionalSubscriber<? super T> actual;

        final PublisherPeekHelper<T> parent;

        Subscription s;

        public PublisherPeekConditionalSubscriber(ConditionalSubscriber<? super T> actual, PublisherPeekHelper<T> parent) {
            this.actual = actual;
            this.parent = parent;
        }

        @Override
        public void request(long n) {
            if(parent.onRequestCall() != null) {
                try {
                    parent.onRequestCall().accept(n);
                }
                catch (Throwable e) {
                    cancel();
                    onError(ExceptionHelper.unwrap(e));
                    return;
                }
            }
            s.request(n);
        }

        @Override
        public void cancel() {
            if(parent.onCancelCall() != null) {
                try {
                    parent.onCancelCall().run();
                }
                catch (Throwable e) {
                    ExceptionHelper.throwIfFatal(e);
                    s.cancel();
                    onError(ExceptionHelper.unwrap(e));
                    return;
                }
            }
            s.cancel();
        }

        @Override
        public void onSubscribe(Subscription s) {
            if(parent.onSubscribeCall() != null) {
                try {
                    parent.onSubscribeCall().accept(s);
                }
                catch (Throwable e) {
                    s.cancel();
                    actual.onSubscribe(EmptySubscription.INSTANCE);
                    onError(e);
                    return;
                }
            }
            this.s = s;
            actual.onSubscribe(this);
        }

        @Override
        public void onNext(T t) {
            if(parent.onNextCall() != null) {
                try {
                    parent.onNextCall().accept(t);
                }
                catch (Throwable e) {
                    cancel();
                    ExceptionHelper.throwIfFatal(e);
                    onError(ExceptionHelper.unwrap(e));
                    return;
                }
            }
            actual.onNext(t);
        }

        @Override
        public boolean tryOnNext(T t) {
            if(parent.onNextCall() != null) {
                try {
                    parent.onNextCall().accept(t);
                }
                catch (Throwable e) {
                    cancel();
                    ExceptionHelper.throwIfFatal(e);
                    onError(ExceptionHelper.unwrap(e));
                    return true;
                }
            }
            return actual.tryOnNext(t);
        }

        @Override
        public void onError(Throwable t) {
            if(parent.onErrorCall() != null) {
                ExceptionHelper.throwIfFatal(t);
                parent.onErrorCall().accept(t);
            }

            actual.onError(t);

            if(parent.onAfterTerminateCall() != null) {
                try {
                    parent.onAfterTerminateCall().run();
                }
                catch (Throwable e) {
                    ExceptionHelper.throwIfFatal(e);
                    Throwable _e = ExceptionHelper.unwrap(e);
                    e.addSuppressed(ExceptionHelper.unwrap(t));
                    if(parent.onErrorCall() != null) {
                        parent.onErrorCall().accept(_e);
                    }
                    actual.onError(_e);
                }
            }
        }

        @Override
        public void onComplete() {
            if(parent.onCompleteCall() != null) {
                try {
                    parent.onCompleteCall().run();
                }
                catch (Throwable e) {
                    ExceptionHelper.throwIfFatal(e);
                    onError(ExceptionHelper.unwrap(e));
                    return;
                }
            }

            actual.onComplete();

            if(parent.onAfterTerminateCall() != null) {
                try {
                    parent.onAfterTerminateCall().run();
                }
                catch (Throwable e) {
                    ExceptionHelper.throwIfFatal(e);
                    Throwable _e = ExceptionHelper.unwrap(e);
                    if(parent.onErrorCall() != null) {
                        parent.onErrorCall().accept(_e);
                    }
                    actual.onError(_e);
                }
            }
        }

        @Override
        public Object downstream() {
            return actual;
        }

        @Override
        public Object upstream() {
            return s;
        }
    }
}
