package com.eucalyptus.ws.handlers;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.Iterator;
import java.util.List;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamReader;

import org.apache.axiom.om.OMAbstractFactory;
import org.apache.axiom.om.OMElement;
import org.apache.axiom.soap.SOAP11Constants;
import org.apache.axiom.soap.SOAPEnvelope;
import org.apache.axiom.soap.SOAPFactory;
import org.apache.axiom.soap.SOAPFault;
import org.apache.axiom.soap.SOAPHeader;
import org.apache.axiom.soap.SOAPHeaderBlock;
import org.apache.axiom.soap.impl.builder.StAXSOAPModelBuilder;
import org.apache.log4j.Logger;
import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBuffers;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.ChannelPipelineCoverage;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.handler.codec.http.HttpHeaders;

import com.eucalyptus.ws.EucalyptusRemoteFault;
import com.eucalyptus.ws.MappingHttpMessage;
import com.eucalyptus.ws.MappingHttpRequest;
import com.eucalyptus.ws.binding.Binding;
import com.google.common.collect.Lists;

import edu.ucsb.eucalyptus.msgs.EucalyptusErrorMessageType;

@ChannelPipelineCoverage("one")
public class WalrusSoapHandler extends MessageStackHandler {
	private static Logger LOG = Logger.getLogger( WalrusSoapHandler.class );
	private final SOAPFactory soapFactory                      = OMAbstractFactory.getSOAP11Factory( );

	@Override
	public void incomingMessage( ChannelHandlerContext ctx, MessageEvent event ) throws Exception {
		if ( event.getMessage( ) instanceof MappingHttpMessage ) {
			final MappingHttpMessage message = ( MappingHttpMessage ) event.getMessage( );
			String content = message.getContent( ).toString( "UTF-8" );
			ByteArrayInputStream byteIn = new ByteArrayInputStream( content.getBytes( ) );
			XMLStreamReader xmlStreamReader = XMLInputFactory.newInstance( ).createXMLStreamReader( byteIn );
			StAXSOAPModelBuilder soapBuilder = new StAXSOAPModelBuilder( xmlStreamReader, SOAP11Constants.SOAP_ENVELOPE_NAMESPACE_URI );
			SOAPEnvelope env = ( SOAPEnvelope ) soapBuilder.getDocumentElement( );
			message.setSoapEnvelope( env );
			message.setMessageString( content );
			if ( !env.hasFault( ) ) {
				message.setOmMessage( env.getBody( ).getFirstElement( ) );
			} else {
				final SOAPHeader header = env.getHeader( );
				final List<SOAPHeaderBlock> headers = Lists.newArrayList( header.examineAllHeaderBlocks( ) );
				// :: try to get the fault info from the soap header -- hello there? :://
				String action = "ProblemAction";
				String relatesTo = "RelatesTo";
				for ( final SOAPHeaderBlock headerBlock : headers ) {
					if ( action.equals( headerBlock.getLocalName( ) ) ) {
						action = headerBlock.getText( );
					} else if ( relatesTo.equals( headerBlock.getLocalName( ) ) ) {
						relatesTo = headerBlock.getText( );
					}
				}
				// :: process the real fault :://
				final SOAPFault fault = env.getBody( ).getFault( );
				String faultReason = "";
				final Iterator children = fault.getChildElements( );
				while ( children.hasNext( ) ) {
					final OMElement child = ( OMElement ) children.next( );
					faultReason += child.getText( );
				}
				final String faultCode = fault.getCode( ).getText( );
				faultReason = faultReason.replaceAll( faultCode, "" );
				throw new EucalyptusRemoteFault( action, relatesTo, faultCode, faultReason );
			}
		}
	}

	@Override
	public void outgoingMessage( ChannelHandlerContext ctx, MessageEvent event ) throws Exception {
		if ( event.getMessage( ) instanceof MappingHttpMessage ) {
			final MappingHttpMessage httpMessage = ( MappingHttpMessage ) event.getMessage( );
			if( httpMessage.getMessage( ) instanceof EucalyptusErrorMessageType ) {
				EucalyptusErrorMessageType errMsg = (EucalyptusErrorMessageType) httpMessage.getMessage( );
				httpMessage.setSoapEnvelope( Binding.createFault( errMsg.getSource( ), errMsg.getMessage( ), errMsg.getStatusMessage( ) ) );
			} else {
				// :: assert sourceElem != null :://
				httpMessage.setSoapEnvelope( this.soapFactory.getDefaultEnvelope( ) );
				httpMessage.getSoapEnvelope( ).getBody( ).addChild( httpMessage.getOmMessage( ) );
			}
			ByteArrayOutputStream byteOut = new ByteArrayOutputStream( );
			httpMessage.getSoapEnvelope( ).serialize( byteOut );
			ChannelBuffer buffer = ChannelBuffers.wrappedBuffer( byteOut.toByteArray( ) );
			httpMessage.addHeader( HttpHeaders.Names.CONTENT_LENGTH, String.valueOf( buffer.readableBytes( ) ) );
			httpMessage.addHeader( HttpHeaders.Names.CONTENT_TYPE, "text/xml; charset=UTF-8" );
			httpMessage.setContent( buffer );
		}
	}
}