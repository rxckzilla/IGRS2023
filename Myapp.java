
/*
 * $Id: EchoServlet.java,v 1.5 2003/06/22 12:32:15 fukuda Exp $
 */
package org.mobicents.servlet.sip.example;

import java.util.*;
import java.io.IOException;

import javax.servlet.sip.SipServlet;	
import javax.servlet.sip.SipServletRequest;
import javax.servlet.sip.SipServletResponse;
import javax.servlet.sip.SipSession;
import javax.servlet.sip.SipApplicationSession;
import javax.servlet.ServletException;
import javax.servlet.sip.URI;
import javax.servlet.sip.Proxy;
import javax.servlet.sip.SipFactory;
import java.nio.charset.StandardCharsets;

/**
 */
public class Myapp extends SipServlet {

	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	static private Map<String, String> EstadosDB;
	static private Map<String, String> RegistrarDB;
	static private SipFactory factory;
	
	public Myapp() {
		super();
		RegistrarDB = new HashMap<String,String>();
		EstadosDB = new HashMap<String, String>();
	}
	
	public void init() {
		factory = (SipFactory) getServletContext().getAttribute(SIP_FACTORY);
	}

	/**
        * Acts as a registrar and location service for REGISTER messages
        * @param  request The SIP message received by the AS 
        */
	protected void doRegister(SipServletRequest request) throws ServletException,
        IOException {
		String aor = getSIPuri(request.getHeader("To"));
		String contact = getSIPuriPort(request.getHeader("Contact"));

		

		if (!aor.endsWith("@acme.pt")) {
			log("Invalid domain in the SIP request: " + aor);
			SipServletResponse response = request.createResponse(403);
			response.send();
			return;
		}
		if ("0".equals(request.getHeader("Expires"))) {
			// Deregistration request, remove the AOR from the RegistrarDB
			RegistrarDB.remove(aor);
			log("Deregistered AOR: " + aor);
		} else {
			// Registration request, add the AOR to the RegistrarDB
			RegistrarDB.put(aor, contact);
			EstadosDB.put(aor, "Disponivel");
			log("Registered AOR: " + aor);
		}
		
		SipServletResponse response = request.createResponse(200);
		response.send();

		// Some logs to show the content of the Registrar database.
		log("REGISTER (myapp):***");
		Iterator<Map.Entry<String, String>> it = RegistrarDB.entrySet().iterator();
		while (it.hasNext()) {
			Map.Entry<String, String> pairs = it.next();
			System.out.println(pairs.getKey() + " = " + pairs.getValue());
		}
		log("REGISTER (myapp):***");
	}

	protected void doResponse(SipServletResponse response) throws ServletException, IOException {
		int responseStatus = response.getStatus();

		if (responseStatus == 200) {
			// Successful response (200 OK) received

			// Extract relevant information from the response
			String toUri = getSIPuri(response.getHeader("To"));
			String fromUri = getSIPuri(response.getHeader("From"));

			// Assuming "gofind@acme.pt" is the 3PCC controller
			if (toUri.equals("sip:gofind@acme.pt")) {
				// Check if this is a response to an INVITE sent to "gofind@acme.pt"

				// Forward the response to the other party (Bob)
				forwardResponseToOtherParty(response, fromUri);
			}
		} else {
			// Handle other response codes as needed
		}
	}

	protected void forwardResponseToOtherParty(SipServletResponse response, String otherPartyUri)
			throws ServletException, IOException {
		// Get the contact information of the other party from the RegistrarDB
		String otherPartyContact = RegistrarDB.get(otherPartyUri);

		// Forward the response to the other party
		SipServletRequest responseToOtherParty = response.createAck();
		responseToOtherParty.setRequestURI(factory.createURI(otherPartyContact));
		responseToOtherParty.send();
	}

	/**
        * Sends SIP replies to INVITE messages
        * - 300 if registred
        * - 404 if not registred
        * @param  request The SIP message received by the AS 
        */
	protected void doInvite(SipServletRequest request)
                  throws ServletException, IOException {
		
		// Some logs to show the content of the Registrar database.
		log("INVITE (myapp):***");
		Iterator<Map.Entry<String,String>> it = RegistrarDB.entrySet().iterator();
    		while (it.hasNext()) {
        		Map.Entry<String,String> pairs = (Map.Entry<String,String>)it.next();
        		System.out.println(pairs.getKey() + " = " + pairs.getValue());
    		}
		log("INVITE (myapp):***");
		
		
		String aor = getSIPuri(request.getHeader("To")); // Get the To AoR
		String aorFrom = getSIPuri(request.getHeader("From")); // Get the From AoR
		String domain = aorFrom.substring(aorFrom.indexOf("@")+1, aorFrom.length());
		String domain2 = aorFrom.substring(aorFrom.indexOf("@")+1, aorFrom.length());
		log(domain);
		
		if (domain.equals("acme.pt") && domain2.equals("acme.pt")) { // The To domain is the same as the server 
	    	if(aor.equals("sip:chat@acme.pt")) {
				Proxy proxy = request.getProxy();
				proxy.setRecordRoute(true);
				proxy.setSupervised(false);
				String chat = "sip:conf@127.0.0.1:5070";
				URI toContact = factory.createURI(chat);
				proxy.proxyTo(toContact);
			}else if (!RegistrarDB.containsKey(aor)) { // To AoR not in the database, reply 404
				SipServletResponse response; 
				response = request.createResponse(404);
				response.send();
	    	}else if (!EstadosDB.get(aor).equals("Disponivel")) {
				SipServletResponse response = request.createResponse(486);
				response.send();
			}else {
				Proxy proxy = request.getProxy();
				proxy.setRecordRoute(true);
				proxy.setSupervised(false);
				URI toContact = factory.createURI(RegistrarDB.get(aor));
				proxy.proxyTo(toContact);
			}			
		}else {
			SipServletResponse response; 
			response = request.createResponse(403);
			response.send();
		}

		/*
	    if (!RegistrarDB.containsKey(aor)) { // To AoR not in the database, reply 404
			SipServletResponse response; 
			response = request.createResponse(404);
			response.send();
	    } else {
			SipServletResponse response = request.createResponse(300);
			// Get the To AoR contact from the database and add it to the response 
			response.setHeader("Contact",RegistrarDB.get(aor));
			response.send();
		}
		*/
	}

	/**
	* Acts as a registrar and location service for REGISTER messages
	* @param  request The SIP message received by the AS 
	*/
	
	 protected void doMessage(SipServletRequest request) throws ServletException, IOException {
		String aorFrom = getSIPuri(request.getHeader("From"));
		String aorTo = getSIPuri(request.getHeader("To"));

		// Log the AORs for debugging
		log("AOR From: " + aorFrom);
		log("AOR To: " + aorTo);

		if (!aorFrom.endsWith("@acme.pt") || !aorTo.endsWith("@acme.pt")) {
			// User does not belong to the restricted group, indicate that the service is not available
			SipServletResponse response = request.createResponse(403);
			response.send();
			return;
		}
		String targetContact = request.getContent().toString();
		if (RegistrarDB.containsKey(targetContact)) {
			// AOR To exists, create a message session and respond with 200 OK

			if(aorTo.equals("sip:gofind@acme.pt")){
				if(EstadosDB.get(targetContact).equals("Disponivel")){
						// Retrieve the session for the recipient
					SipServletRequest messageRequest = factory.createRequest(
							request.getApplicationSession(),
							"INVITE",
							aorFrom,
							targetContact
					);
					// Store the session information for tracking
					request.getApplicationSession().setAttribute("callSession", messageRequest.getSession());

					// Send the 200 OK answer2' back to Alice
					SipServletResponse response_recipent = request.createResponse(200);
					response_recipent.setContent("answer2'", "application/sdp");
					response_recipent.send();


					messageRequest.send();

					SipServletResponse response = request.createResponse(200);
					response.send();
					
				}else{
					SipServletResponse response = request.createResponse(486);
					response.send();
				}
				
				
				
			}else{
				SipServletResponse response = request.createResponse(403);
				response.send();
			}

			
		} else {
			// Log the RegistrarDB content for debugging
			log("RegistrarDB Content: " + RegistrarDB);

			// AOR To does not exist, respond with 404 Not Found
			SipServletResponse response = request.createResponse(404);
			response.send();
		}
	}

	


	


	
	/**
	 * Sends an alert message to the specified recipient.
	 *
	 * @param request       The SIP message received by the AS
	 * @param aorTo  The AOR of the recipient
	 * @param alertMessage  The alert message content
	 */
	protected void sendAlertToRecipient(SipServletRequest request, String aorTo, String alertMessage) throws IOException, ServletException {
        if (RegistrarDB.containsKey(aorTo)) {
            SipServletRequest alertRequest = factory.createRequest(
                    request.getApplicationSession(),
                    "MESSAGE",
                    aorTo,
                    RegistrarDB.get(aorTo)
            );

            alertRequest.setContent(alertMessage.getBytes(StandardCharsets.UTF_8), "text/plain");
            alertRequest.send();
        }
    }

	protected void doAck(SipServletRequest request) throws ServletException, IOException {
		String fromAor = getSIPuri(request.getHeader("From"));
		String toAor = getSIPuri(request.getHeader("To"));

		if (!toAor.equals("sip:chat@acme.pt")) {
			EstadosDB.put(fromAor, "Ocupado");
			EstadosDB.put(toAor, "Ocupado");
		} else {
			EstadosDB.put(fromAor, "Em conferencia");
		}
	}


	protected void doBye(SipServletRequest request) throws ServletException, IOException {
		String fromAor = getSIPuri(request.getHeader("From"));
		String toAor = getSIPuri(request.getHeader("To"));

		if (!toAor.equals("sip:chat@acme.pt")) {
			EstadosDB.put(fromAor, "Disponivel");
			EstadosDB.put(toAor, "Disponivel");
		} else {
			EstadosDB.put(fromAor, "Disponivel");
		}

	}




/*
        * Auxiliary function for extracting SPI URIs
        * @param  uri A URI with optional extra attributes 
        * @return SIP URI 
        */
	protected String getSIPuri(String uri) {
		String f = uri.substring(uri.indexOf("<")+1, uri.indexOf(">"));
		int indexCollon = f.indexOf(":", f.indexOf("@"));
		if (indexCollon != -1) {
			f = f.substring(0,indexCollon);
		}
		return f;
	}

	/**
        * Auxiliary function for extracting SPI URIs
        * @param  uri A URI with optional extra attributes 
        * @return SIP URI and port 
        */
	protected String getSIPuriPort(String uri) {
		String f = uri.substring(uri.indexOf("<")+1, uri.indexOf(">"));
		return f;
	}
}
