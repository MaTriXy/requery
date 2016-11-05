/*
 * Copyright 2016 requery.io
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.requery.reactivex;

import io.reactivex.Flowable;
import io.reactivex.Observable;
import io.reactivex.functions.Function;
import io.reactivex.functions.Predicate;
import io.requery.BlockingEntityStore;
import io.requery.TransactionListenable;
import io.requery.meta.Type;
import io.requery.query.BaseResult;
import io.requery.query.Result;
import io.requery.query.element.QueryElement;
import io.requery.query.element.QueryWrapper;
import io.requery.rx.RxSupport;
import org.reactivestreams.Subscriber;

import java.util.Collections;
import java.util.Set;

/**
 * Support class for use with RxJava 2.0
 */
public final class ReactiveSupport {

    private static final TransactionListenerSupplier typeChanges = new TransactionListenerSupplier();

    private ReactiveSupport() {
    }

    public static <S> ReactiveEntityStore<S> toReactiveStore(BlockingEntityStore<S> store) {
        return new WrappedEntityStore<>(store);
    }

    public static <T> Observable<Result<T>> toObservableResult(final Result<T> result) {
        if (!(result instanceof TransactionListenable)) {
            throw new UnsupportedOperationException();
        }
        TransactionListenable listenable = (TransactionListenable) result;
        final QueryElement<?> element = ((QueryWrapper) result).unwrapQuery();
        // ensure the transaction listener is added in the target data store
        listenable.addTransactionListener(typeChanges);
        return typeChanges.commitSubject()
            .filter(new Predicate<Set<Type<?>>>() {
                @Override
                public boolean test(Set<Type<?>> types) {
                    return !Collections.disjoint(element.entityTypes(), types) ||
                           RxSupport.referencesType(element.entityTypes(), types);
                }
            }).map(new Function<Set<Type<?>>, Result<T>>() {
                @Override
                public Result<T> apply(Set<Type<?>> types) {
                    return result;
                }
            }).startWith(result);
    }

    public static <E> Flowable<E> toFlowable(final BaseResult<E> result) {
        return new Flowable<E>() {
            @Override
            protected void subscribeActual(Subscriber<? super E> s) {
                s.onSubscribe(new QuerySubscription<>(result, s));
            }
        };
    }
}
