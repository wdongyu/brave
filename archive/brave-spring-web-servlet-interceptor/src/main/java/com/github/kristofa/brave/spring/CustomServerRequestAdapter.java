package com.github.kristofa.brave.spring;

import static com.github.kristofa.brave.IdConversion.convertToLong;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;

import com.github.kristofa.brave.KeyValueAnnotation;
import com.github.kristofa.brave.ServerRequestAdapter;
import com.github.kristofa.brave.SpanId;
import com.github.kristofa.brave.TraceData;
import com.github.kristofa.brave.http.BraveHttpHeaders;
import com.github.kristofa.brave.http.SpanNameProvider;

import zipkin.TraceKeys;

public class CustomServerRequestAdapter  implements ServerRequestAdapter {
    private final HttpServletRequest request;

    public CustomServerRequestAdapter(HttpServletRequest request, SpanNameProvider spanNameProvider) {
        this.request = request;
    }

    @Override
    public TraceData getTraceData() {
        String sampled = request.getHeader(BraveHttpHeaders.Sampled.getName());
        String parentSpanId = request.getHeader(BraveHttpHeaders.ParentSpanId.getName());
        String traceId = request.getHeader(BraveHttpHeaders.TraceId.getName());
        String spanId = request.getHeader(BraveHttpHeaders.SpanId.getName());

        // Official sampled value is 1, though some old instrumentation send true
        Boolean parsedSampled = sampled != null
            ? sampled.equals("1") || sampled.equalsIgnoreCase("true")
            : null;

        if (traceId != null && spanId != null) {
            return TraceData.create(getSpanId(traceId, spanId, parentSpanId, parsedSampled));
        } else if (parsedSampled == null) {
            return TraceData.EMPTY;
        } else if (parsedSampled.booleanValue()) {
            // Invalid: The caller requests the trace to be sampled, but didn't pass IDs
            return TraceData.EMPTY;
        } else {
            return TraceData.NOT_SAMPLED;
        }
    }

    @Override
    public String getSpanName() {
        return "custom span";
    }

    @Override
    public Collection<KeyValueAnnotation> requestAnnotations() {
        List<KeyValueAnnotation> kvs = new ArrayList<KeyValueAnnotation>();
        
    	Map<String, String[]> params = this.request.getParameterMap();
    	for(String key:params.keySet()){
    		KeyValueAnnotation kv = KeyValueAnnotation.create(key, params.get(key)[0]);
    		kvs.add(kv);
    	}
    	
        KeyValueAnnotation uriAnnotation = KeyValueAnnotation.create(
                TraceKeys.HTTP_URL, request.getRequestURI().toString() + "1");
        kvs.add(uriAnnotation);
        return kvs;
    }

    static SpanId getSpanId(String traceId, String spanId, String parentSpanId, Boolean sampled) {
        return SpanId.builder()
            .traceIdHigh(traceId.length() == 32 ? convertToLong(traceId, 0) : 0)
            .traceId(convertToLong(traceId))
            .spanId(convertToLong(spanId))
            .sampled(sampled)
            .parentId(parentSpanId == null ? null : convertToLong(parentSpanId)).build();
   }
}
