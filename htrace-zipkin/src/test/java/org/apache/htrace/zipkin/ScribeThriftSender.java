/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.htrace.zipkin;

import org.apache.htrace.Transport;
import org.apache.htrace.core.*;
import org.apache.htrace.impl.ScribeTransport;
import org.apache.htrace.impl.ZipkinSpanReceiver;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import zipkin.collector.CollectorMetrics;
import zipkin.collector.scribe.ScribeCollector;
import zipkin.storage.InMemoryStorage;
import zipkin.storage.QueryRequest;

import java.util.List;

public class ScribeThriftSender {

    InMemoryStorage storage = new InMemoryStorage();
    ScribeCollector collector;

    @Before
    public void start() {
        collector = ScribeCollector.builder()
                .metrics(CollectorMetrics.NOOP_METRICS)
                .storage(storage).build();
        collector.start();
    }

    @After
    public void close() {
        collector.close();
    }

    ScribeTransport sender = new ScribeTransport();

    private Tracer newTracer(final Transport transport) {
        TracerPool pool = new TracerPool("newTracer");
        pool.addReceiver(new ZipkinSpanReceiver(HTraceConfiguration.EMPTY) {
            @Override
            protected Transport createTransport(HTraceConfiguration conf) {
                return transport;
            }
        });
        return new Tracer.Builder("ZipkinTracer").
                tracerPool(pool).
                conf(HTraceConfiguration.fromKeyValuePairs(
                        "sampler.classes", AlwaysSampler.class.getName()
                )).
                build();
    }

    @Test
    public void sendsSpans() throws Exception {
        Tracer t = newTracer(sender);
        TraceScope s = t.newScope("root");
        String originalValue = "bar";
        s.addKVAnnotation("foo", originalValue);
        s.close();
        List<List<zipkin.Span>> spans = storage.spanStore().getTraces(QueryRequest.builder().build());
        Assert.assertEquals(new String(spans.listIterator().next().get(0).binaryAnnotations.get(0).value), originalValue);
        t.close();
    }
}
