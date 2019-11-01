/* Copyright (c) 2012 Google Inc.
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

package com.bbrister.metrodrone;

import android.text.TextUtils;
import android.util.Base64;
import android.util.Log;

import com.android.billingclient.api.Purchase;

import java.security.InvalidKeyException;
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.Signature;
import java.security.SignatureException;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.X509EncodedKeySpec;

/**
 * Security-related methods. For a secure implementation, all of this code
 * should be implemented on a server that communicates with the
 * application on the device. For the sake of simplicity and clarity of this
 * example, this code is included here and is executed on the device. If you
 * must verify the purchases on the phone, you should obfuscate this code to
 * make it harder for an attacker to replace the code with stubs that treat all
 * purchases as verified.
 */
public class Security {
    private static final String TAG = "Security";

    private static final String KEY_FACTORY_ALGORITHM = "RSA";
    private static final String SIGNATURE_ALGORITHM = "SHA1withRSA";

    private static final String BASE_64_ENCODED_PUBLIC_KEY = "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAu+hGsruU+wSDIEHGrStl3pAUufOpbQn+O7UdbhfemkEyfU8lEOeLQLID/rmgOkBJcLcoGCfW/8+U20IB9C0Mx2fmBIE7D47h+Y/7V2AmS1UMdp49MHSooY3ND8C+vQjpz7zazMF9nUKGIHe2VKvAFKi+a7jZ8Uvfh7+5HTnKNLizDu5F8iUoGm9gJcUHnK6oDR/fgILBII7/jze/RDltG5IVNO1IZ3Tt6MoScQc+460KRfpMalLwl8A2fpbxT+SXbIsGxXj+vhWh9RQFAY7W0YutiEHu4gX/iIP66LJtou8XXgKoDDa9nQoArr9gJssQqmBTPF+cE4AKZRdAV2NSVwIDAQAB";

    /**
     * Convenience wrapper for verifyValidSignature.
     */
    public static boolean verifyPurchase(final Purchase purchase) {
        return verifyValidSignature(purchase.getOriginalJson(), purchase.getSignature());
    }

    /**
     * Verifies that the purchase was signed correctly for this developer's public key.
     * <p>Note: It's strongly recommended to perform such check on your backend since hackers can
     * replace this method with "constant true" if they decompile/rebuild your app.
     * </p>
     */
    private static boolean verifyValidSignature(String signedData, String signature) {
        try {
            return Security.verifyPurchase(BASE_64_ENCODED_PUBLIC_KEY, signedData, signature);
        } catch (Exception e) {
            if (BuildConfig.DEBUG) {
                Log.e(TAG, "Got an exception trying to validate a purchase: " + e);
            }
            return false;
        }
    }

    /**
     * Verifies that the data was signed with the given signature, and returns
     * the verified purchase. The data is in JSON format and signed
     * with a private key. The data also contains the {@link PurchaseState}
     * and product ID of the purchase.
     * @param base64PublicKey the base64-encoded public key to use for verifying.
     * @param signedData the signed JSON string (signed, not encrypted)
     * @param signature the signature for the data, signed with the private key
     */
    private static boolean verifyPurchase(String base64PublicKey, String signedData,
                                         String signature) {
        if (TextUtils.isEmpty(signedData) || TextUtils.isEmpty(base64PublicKey) ||
                TextUtils.isEmpty(signature)) {
            if (BuildConfig.DEBUG_EXCEPTIONS) {
                throw new DebugException("Purchase verification failed: missing data.");
            }
            return false;
        }

        PublicKey key = generatePublicKey(base64PublicKey);
        return Security.verify(key, signedData, signature);
    }

    /**
     * Generates a PublicKey instance from a string containing the
     * Base64-encoded public key.
     *
     * @param encodedPublicKey Base64-encoded public key
     * @throws IllegalArgumentException if encodedPublicKey is invalid
     */
    private static PublicKey generatePublicKey(String encodedPublicKey) {
        try {
            byte[] decodedKey = Base64.decode(encodedPublicKey, Base64.DEFAULT);
            KeyFactory keyFactory = KeyFactory.getInstance(KEY_FACTORY_ALGORITHM);
            return keyFactory.generatePublic(new X509EncodedKeySpec(decodedKey));
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        } catch (InvalidKeySpecException e) {
            if (BuildConfig.DEBUG) {
                Log.e(TAG, "Invalid key specification.");
            }
            throw new IllegalArgumentException(e);
        }
    }

    /**
     * Verifies that the signature from the server matches the computed
     * signature on the data.  Returns true if the data is correctly signed.
     *
     * @param publicKey public key associated with the developer account
     * @param signedData signed data from server
     * @param signature server signature
     * @return true if the data and signature match
     */
    private static boolean verify(PublicKey publicKey, String signedData, String signature) {
        byte[] signatureBytes;
        try {
            signatureBytes = Base64.decode(signature, Base64.DEFAULT);
        } catch (IllegalArgumentException e) {
            if (BuildConfig.DEBUG) {
                Log.e(TAG, "Base64 decoding failed.");
            }
            throw e;
        }
        try {
            Signature sig = Signature.getInstance(SIGNATURE_ALGORITHM);
            sig.initVerify(publicKey);
            sig.update(signedData.getBytes());
            if (!sig.verify(signatureBytes)) {
                if (BuildConfig.DEBUG) {
                    Log.e(TAG, "Signature verification failed.");
                }
                return false;
            }
            return true;
        } catch (NoSuchAlgorithmException e) {
            if (BuildConfig.DEBUG) {
                Log.e(TAG, "NoSuchAlgorithmException.");
            }
        } catch (InvalidKeyException e) {
            if (BuildConfig.DEBUG) {
                Log.e(TAG, "Invalid key specification.");
            }
        } catch (SignatureException e) {
            if (BuildConfig.DEBUG) {
                Log.e(TAG, "Signature exception.");
            }
        }
        return false;
    }
}