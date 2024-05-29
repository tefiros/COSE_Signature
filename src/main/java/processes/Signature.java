/**
 * 
 */
package processes;

import java.io.FileInputStream;
import java.io.IOException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.Security;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;
import java.util.Base64;

import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.jdom2.Document;
import org.jdom2.Element;
import org.jdom2.JDOMException;
import org.jdom2.Namespace;

import com.upokecenter.cbor.CBORObject;

import COSE.AlgorithmID;
import COSE.Attribute;
import COSE.CoseException;
import COSE.HeaderKeys;
import COSE.KeyKeys;
import COSE.OneKey;
import COSE.Sign1Message;

/**
 * This class implements the method for signing a message with COSE signatures
 * using COSE_Sign1 structures with null payload
 * 
 * @author idb0095
 */
public class Signature extends XMLFileManagement {

	static {
		// Register BouncyCastle provider
		Security.addProvider(new BouncyCastleProvider());
	}

	private OneKey privateKey(String kid) {

		OneKey keyPair = null;
		Parameters param = new Parameters();

		char pswd[] = param.readpswd().toCharArray();
		KeyStore ks;

		try {
			ks = KeyStore.getInstance(param.readInstanceKeyStore());

			ks.load(new FileInputStream(param.readSignerKeyStore()), pswd);

			if (ks.containsAlias(kid)) {

				PrivateKey privateKey = (PrivateKey) ks.getKey(kid, pswd);

				keyPair = new OneKey(null, privateKey);

				// Specify key type
				if (privateKey.getAlgorithm().equals("EC")) {

					keyPair.add(KeyKeys.Algorithm, AlgorithmID.ECDSA_256.AsCBOR());

				} else if (privateKey.getAlgorithm().equals("RSA")) {

					keyPair.add(KeyKeys.KeyType, KeyKeys.KeyType_RSA);
					keyPair.add(KeyKeys.Algorithm, AlgorithmID.RSA_PSS_512.AsCBOR());

				} else if (privateKey.getAlgorithm().equals("EdDSA")) {
					// error al crear clave COSE con eddsa
					keyPair.add(KeyKeys.Algorithm, AlgorithmID.EDDSA.AsCBOR());

				}

				keyPair.add(KeyKeys.KeyId, CBORObject.FromObject(kid));

			} else {
				throw new COSESignatureException("There is no key with this ID: " + kid);
			}

		} catch (KeyStoreException | NoSuchAlgorithmException | CertificateException | IOException
				| COSESignatureException | CoseException | UnrecoverableKeyException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		return keyPair;

	}

	/**
	 * This method creates a COSE_Sign1 object with nil payload and protected
	 * algorithm tag attributes and signs the set message using the private key.
	 * 
	 * @param document   message to be signed, it does not appear in the structure
	 * @param privateKey key to be used to sign the message
	 * @return the serialized signature
	 * @throws CoseException
	 * @throws COSESignatureException
	 */
	public String signing(String document, String kid) throws CoseException, COSESignatureException {

		// Creates a COSE_Sign1 object with null payload
		Sign1Message sign1Message = new Sign1Message(true, false);
		// Set message to sign
		sign1Message.SetContent(document);

		OneKey privateKey;
		Parameters param = new Parameters();

		privateKey = privateKey(kid);
		// Add protected attributes with algorithm tags
		if (privateKey.HasAlgorithmID(AlgorithmID.ECDSA_256)) {
			sign1Message.addAttribute(HeaderKeys.Algorithm, AlgorithmID.ECDSA_256.AsCBOR(), Attribute.PROTECTED);
		} else if (privateKey.HasAlgorithmID(AlgorithmID.RSA_PSS_512)) {
			sign1Message.addAttribute(HeaderKeys.Algorithm, AlgorithmID.RSA_PSS_512.AsCBOR(), Attribute.PROTECTED);
		} else if (privateKey.HasAlgorithmID(AlgorithmID.EDDSA)) {
			throw new COSESignatureException(
					"EdDSA algorithm is not available in the version of the cose library used");
			// sign1Message.addAttribute(HeaderKeys.Algorithm, AlgorithmID.EDDSA.AsCBOR(),
			// Attribute.PROTECTED);
		} else
			throw new COSESignatureException("No valid algorithm found");

		sign1Message.addAttribute(HeaderKeys.CONTENT_TYPE, CBORObject.FromObject(param.readContentType()),
				Attribute.PROTECTED);
		// Add protected attributes with KID tag
		sign1Message.addAttribute(HeaderKeys.KID, privateKey.get(KeyKeys.KeyId), Attribute.PROTECTED);

		// Sign the message
		sign1Message.sign(privateKey);

		String signatureString = Base64.getEncoder().encodeToString(sign1Message.EncodeToBytes());

		return signatureString;
	}

	/**
	 * This is the methos related to the first enclosing method proposed
	 * 
	 * @param YANGFile  xml file where the signature is to be enclosed
	 * @param signature signature to include in the YANG data provenance
	 * @return JDOM of the YANG data provenance with the new signature element
	 * @throws JDOMException
	 * @throws IOException
	 */
	public Document enclosingMethod(String YANGFile, String signature) {

		Parameters param = new Parameters();
		Document document = null;

		// Charge the existing XML file
		try {
			document = loadXMLDocument(YANGFile);

			Element rootElementDocument = document.getRootElement();
			Namespace namespace = rootElementDocument.getNamespace();

			// Get bage64 provenance signature so we can store it inside the YANG structure
			Element signatureElement = new Element(param.readSignElement(), namespace);
			signatureElement.setText(signature);

			// Add the new provenance-string element to the root element
			rootElementDocument.addContent(0, signatureElement);

			// Save the updated XML document that includes the content and the signature
			// saveXMLDocument(document, signatureFile);

		} catch (JDOMException | IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		return document;

	}

}