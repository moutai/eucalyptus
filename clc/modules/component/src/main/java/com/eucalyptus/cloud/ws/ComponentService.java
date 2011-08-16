/*
 * Software License Agreement (BSD License)
 *
 * Copyright (c) 2008,Regents of the University of California
 * All rights reserved.
 *
 * Redistribution and use of this software in source and binary forms,with or
 * without modification,are permitted provided that the following conditions
 * are met:
 *
 * * Redistributions of source code must retain the above
 *   copyright notice,this list of conditions and the
 *   following disclaimer.
 *
 * * Redistributions in binary form must reproduce the above
 *   copyright notice,this list of conditions and the
 *   following disclaimer in the documentation and/or other
 *   materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES,INCLUDING,BUT NOT LIMITED TO,THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT,INDIRECT,INCIDENTAL,SPECIAL,EXEMPLARY,OR
 * CONSEQUENTIAL DAMAGES (INCLUDING,BUT NOT LIMITED TO,PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,DATA,OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,WHETHER IN
 * CONTRACT,STRICT LIABILITY,OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE,EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 *
 * Author: Neil Soman neil@eucalyptus.com
 */

package com.eucalyptus.cloud.ws;

import java.net.InetAddress;
import org.apache.log4j.Logger;
import org.mule.RequestContext;
import com.eucalyptus.component.Component;
import com.eucalyptus.component.ComponentId;
import com.eucalyptus.component.ComponentIds;
import com.eucalyptus.component.Components;
import com.eucalyptus.component.ServiceConfiguration;
import com.eucalyptus.component.ServiceConfigurations;
import com.eucalyptus.context.ServiceContext;
import com.eucalyptus.util.EucalyptusCloudException;
import com.eucalyptus.util.async.AsyncRequests;
import edu.ucsb.eucalyptus.msgs.BaseMessage;
import edu.ucsb.eucalyptus.msgs.ComponentMessageType;

public class ComponentService {

	private static Logger LOG = Logger.getLogger(ComponentService.class);

	public BaseMessage handle(ComponentMessageType request) throws Exception {
		String component = request.getComponent();
		String host = request.getHost();
		String name = request.getName();
		
		LOG.info("Component: "+component+"@"+host);
		ServiceConfiguration service = this.lookupService(component,host,name);
    try {
      BaseMessage reply = null;
  		if(service.isVmLocal()) {//send direct to local component using mule registry directly
        reply = ServiceContext.send(service.getComponentId().getLocalEndpointName(),request);
   		} else {//send remote
        reply = AsyncRequests.sendSync(service,request);
   		}
      return reply;
    } catch (Throwable e) {
      LOG.error(e);
      throw new EucalyptusCloudException("Unable to dispatch message to: "+service.getName());
    }
	}

  private ServiceConfiguration lookupService(String component,String name,String host) throws EucalyptusCloudException {
    ComponentId destCompId = ComponentIds.lookup(component);
    if(name != null) {
      return ServiceConfigurations.lookupByName(destCompId.getClass(),name);
    } else if (host != null) {
      return ServiceConfigurations.lookupByHost(destCompId.getClass(),name);
    } else {
      throw new EucalyptusCloudException("Unable to dispatch message to: "+component+"@"+host);
    }
  }
}
