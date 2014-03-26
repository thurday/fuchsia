/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package com.google.code.cxf.protobuf.utils;

import com.google.protobuf.Message;
import com.google.protobuf.RpcCallback;
import com.google.protobuf.RpcChannel;
import com.google.protobuf.RpcController;
import com.google.protobuf.Service;
import com.google.protobuf.Descriptors.MethodDescriptor;

/**
 * TODO: type comment.
 */
public class LocalRPCChannel implements RpcChannel {
    private Service service;

    /**
     * @param service
     */
    public LocalRPCChannel(Service service) {
        super();
        this.service = service;
    }

    /**
     * @see com.google.protobuf.RpcChannel#callMethod(com.google.protobuf.Descriptors.MethodDescriptor,
     * com.google.protobuf.RpcController, com.google.protobuf.Message, com.google.protobuf.Message,
     * com.google.protobuf.RpcCallback)
     */
    public void callMethod(MethodDescriptor method, RpcController rpcController, Message request,
                           Message responsePrototype, RpcCallback<Message> rpcCallback) {
        service.callMethod(method, rpcController, request, rpcCallback);
    }

    public Service getService() {
        return service;
    }

    public void setService(Service service) {
        this.service = service;
    }

}
