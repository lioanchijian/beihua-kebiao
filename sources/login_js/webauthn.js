/**********************************
 * Base64 Core
 **********************************/
((root, factory) => {
    if (typeof define === 'function' && define.amd) {
        define(['base64js'], factory);
    } else if (typeof module === 'object' && module.exports) {
        module.exports = factory(require('base64js'));
    } else {
        root.base64url = factory(root.base64js);
    }
})(this, base64js => {

    function ensureUint8Array(arg) {
        if (arg instanceof ArrayBuffer) {
            return new Uint8Array(arg);
        } else {
            return arg;
        }
    }

    function base64UrlToMime(code) {
        return code.replace(/-/g, '+').replace(/_/g, '/') + '===='.substring(0, (4 - (code.length % 4)) % 4);
    }

    function mimeBase64ToUrl(code) {
        return code.replace(/\+/g, '-').replace(/\//g, '_').replace(/=/g, '');
    }

    function fromByteArray(bytes) {
        return mimeBase64ToUrl(base64js.fromByteArray(ensureUint8Array(bytes)));
    }

    function toByteArray(code) {
        return base64js.toByteArray(base64UrlToMime(code));
    }

    return {
        fromByteArray: fromByteArray,
        toByteArray: toByteArray,
    };

});


/*******************************************************
 * WebAuthN Core
 *******************************************************/

(function (root, factory) {
    if (typeof define === 'function' && define.amd) {
        define(['base64url'], factory);
    } else if (typeof module === 'object' && module.exports) {
        module.exports = factory(require('base64url'));
    } else {
        root.webauthn = factory(root.base64url);
    }
})(this, function (base64url) {

    function extend(obj, more) {
        return Object.assign({}, obj, more);
    }

    /**
     * Create a WebAuthn credential.
     *
     * @param request: object - A PublicKeyCredentialCreationOptions object, except
     *   where binary values are base64url encoded strings instead of byte arrays
     *
     * @return a PublicKeyCredentialCreationOptions suitable for passing as the
     *   `publicKey` parameter to `navigator.credentials.create()`
     */
    function decodePublicKeyCredentialCreationOptions(request) {
        const excludeCredentials = request.excludeCredentials.map(credential => extend(
            credential, {
            id: base64url.toByteArray(credential.id),
        }));

        const publicKeyCredentialCreationOptions = extend(
            request, {
            attestation: 'direct',
            user: extend(
                request.user, {
                id: base64url.toByteArray(request.user.id),
            }),
            challenge: base64url.toByteArray(request.challenge),
            excludeCredentials,
        });

        return publicKeyCredentialCreationOptions;
    }

    /**
     * Create a WebAuthn credential.
     *
     * @param request: object - A PublicKeyCredentialCreationOptions object, except
     *   where binary values are base64url encoded strings instead of byte arrays
     *
     * @return the Promise returned by `navigator.credentials.create`
     */
    function createCredential(request) {
        return navigator.credentials.create({
            publicKey: decodePublicKeyCredentialCreationOptions(request),
        });
    }

    /**
     * Perform a WebAuthn assertion.
     *
     * @param request: object - A PublicKeyCredentialRequestOptions object,
     *   except where binary values are base64url encoded strings instead of byte
     *   arrays
     *
     * @return a PublicKeyCredentialRequestOptions suitable for passing as the
     *   `publicKey` parameter to `navigator.credentials.get()`
     */
    function decodePublicKeyCredentialRequestOptions(request) {
        const allowCredentials = request.allowCredentials && request.allowCredentials.map(credential => extend(
            credential, {
            id: base64url.toByteArray(credential.id),
        }));

        const publicKeyCredentialRequestOptions = extend(
            request, {
            allowCredentials,
            challenge: base64url.toByteArray(request.challenge),
        });

        return publicKeyCredentialRequestOptions;
    }

    /**
     * Perform a WebAuthn assertion.
     *
     * @param request: object - A PublicKeyCredentialRequestOptions object,
     *   except where binary values are base64url encoded strings instead of byte
     *   arrays
     *
     * @return the Promise returned by `navigator.credentials.get`
     */
    function getAssertion(request) {
        return navigator.credentials.get({
            publicKey: decodePublicKeyCredentialRequestOptions(request),
        });
    }


    /** Turn a PublicKeyCredential object into a plain object with base64url encoded binary values */
    function responseToObject(response) {
        let clientExtensionResults = {};

        try {
            clientExtensionResults = response.getClientExtensionResults();
        } catch (e) {
            console.error('getClientExtensionResults failed', e);
        }

        if (response.response.attestationObject) {
            return {
                type: response.type,
                id: response.id,
                response: {
                    attestationObject: base64url.fromByteArray(response.response.attestationObject),
                    clientDataJSON: base64url.fromByteArray(response.response.clientDataJSON),
                },
                clientExtensionResults,
            };
        } else {
            return {
                type: response.type,
                id: response.id,
                response: {
                    authenticatorData: base64url.fromByteArray(response.response.authenticatorData),
                    clientDataJSON: base64url.fromByteArray(response.response.clientDataJSON),
                    signature: base64url.fromByteArray(response.response.signature),
                    userHandle: response.response.userHandle && base64url.fromByteArray(response.response.userHandle),
                },
                clientExtensionResults,
            };
        }
    }

    return {
        decodePublicKeyCredentialCreationOptions,
        decodePublicKeyCredentialRequestOptions,
        createCredential,
        getAssertion,
        responseToObject,
    };

});


/*****************************************************
 * WebAuthn Utilities
 *****************************************************/

/**
 * RSA公钥 - 用于加密AES密钥
 * 与后端配置的公钥保持一致
 */
const RSA_PUBLIC_KEY = `-----BEGIN PUBLIC KEY-----
MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAjVr1zKwohU3xA0afprWLSQvIymaSH/V27MedFc+CecXSnORIFMAp4uEIb4taDq/2X4eMeTI66Mu/rB5GKSFDbExF2Gu4NaO/CNDpf1gHMScUrIFCh4CDqzBnx17kclvezLkIK0T8FVa4cRsINvzjbnA6jUSMaf6Fm1n9wTAtW6QYBjssGOEtCj+c38PTBdFMmJbXp3brt1tEBesz6lb3Fjp76FGvDZ08xtYG8fxYPuiMwKU04eS+mcX/BunwgpU3zwekHYB+PWRIvq0lBry9Wms25sJE5T/RAv5fEuMLbBkfcZK3+7ivSZthTmPpr2Ap/ji70ZZ6u2jvR5VJq+LJHQIDAQAB
-----END PUBLIC KEY-----`;

/**
 * 生成随机AES密钥（Base64编码）
 */
function generateRandomAesKey() {
    const salt = CryptoJS.lib.WordArray.random(16);
    return CryptoJS.enc.Base64.stringify(salt);
}

/**
 * AES加密（ECB模式）
 */
function aesEncryptNew(key, content) {
    const keyHex = CryptoJS.enc.Base64.parse(key);
    const encString = CryptoJS.AES.encrypt(content, keyHex, {
        mode: CryptoJS.mode.ECB,
        padding: CryptoJS.pad.Pkcs7
    });
    return encString.toString();
}

/**
 * AES解密（ECB模式）
 */
function aesDecryptNew(key, content) {
    const keyHex = CryptoJS.enc.Base64.parse(key);
    const decString = CryptoJS.AES.decrypt(content, keyHex, {
        mode: CryptoJS.mode.ECB,
        padding: CryptoJS.pad.Pkcs7
    });
    return decString.toString(CryptoJS.enc.Utf8);
}

/**
 * 递归解密响应数据（处理多层加密）
 */
function getUnPackBody(key, body) {
    let dec = '';
    let isJSON = false;

    const convertData = (bodyData) => {
        dec = aesDecryptNew(key, bodyData);
        isJSON = false;

        if (dec !== '') {
            try {
                JSON.parse(dec);
                isJSON = true;
                return;
            } catch (str_error) {
                isJSON = false;
            }
            if (!isJSON) {
                convertData(dec);
            }
        }
    };

    convertData(body);
    return JSON.parse(dec || null);
}

/**
 * RSA加密（使用公钥加密AES密钥）
 */
function rsaEncrypt(publicKey, content) {
    const encryptor = new JSEncrypt();
    encryptor.setPublicKey(publicKey);
    const encrypted = encryptor.encrypt(content);

    if (!encrypted) {
        throw new Error('RSA加密失败');
    }

    return encrypted;
}

let ceremonyState = {};
let session = {};
let currentAesKey = null; // 存储当前请求的AES密钥

function checkhascrypto(api_url) {
    return window.configUrl['SID'].includes(api_url)
}
function rejectIfNotSuccess(response) {
    if (response.success) {
        return response;
    }
    return new Promise((resolve, reject) => reject(response));
}

function updateSession(response) {
    if (response.sessionToken) {
        session.sessionToken = response.sessionToken;
    } else {
        session.sessionToken = null;
    }
    if (response.username) {
        session.username = response.username;
    } else {
        session.username = null;
    }
    return response;
}

function rejected(err) {
    return new Promise((resolve, reject) => reject(err));
}

function getWebAuthnUrls() {
    let endpoints = {
        authenticate: "webauthn/authenticate",
        register: "webauthn/register",
    };
    return new Promise((resolve, reject) => resolve(endpoints)).then(data => {
        return data;
    });
}

function getRegisterRequest(urls,
    username,
    displayName,
    credentialNickname,
    requireResidentKey = false) {

    return fetch(urls.register, {
        body: new URLSearchParams({
            username,
            displayName,
            credentialNickname,
            requireResidentKey,
            sessionToken: session.sessionToken || null,
        }),
        headers: {
            "X-CSRF-TOKEN": "csrfToken",
            // "Hascrypto": checkhascrypto()
        },
        method: 'POST',
    })
        .then(response => response.json())
        .then(updateSession)
        .then(rejectIfNotSuccess);
}

function executeRegisterRequest(request) {
    try {
        return webauthn.createCredential(request.publicKeyCredentialCreationOptions);
    } catch (error) {
      // 如果存在全局函数，则调用
    if (typeof window.addPasskeyNotSupportUa === 'function') {
      window.addPasskeyNotSupportUa();
    }
        return rejected(error);
    }
}

function submitResponse(url, request, response) {
    const body = {
        requestId: request.requestId,
        credential: response,
        sessionToken: request.sessionToken || session.sessionToken || null,
    };

    return fetch(url, {
        method: 'POST',
        headers: {
            "X-CSRF-TOKEN": "csrfToken",
            // "Hascrypto": checkhascrypto()
        },
        body: JSON.stringify(body),
    })
        .then(response => response.json())
        .then(updateSession)
        .then(rejectIfNotSuccess)
}

function performCeremony(params) {
    const callbacks = params.callbacks || {};
    const getWebAuthnUrls = params.getWebAuthnUrls;
    const getRequest = params.getRequest;
    const statusStrings = params.statusStrings;
    const executeRequest = params.executeRequest;
    return getWebAuthnUrls()
        .then(urls => {
            if (callbacks.init) {
                callbacks.init(urls);
            }
            return getRequest(urls);
        })

        .then((params) => {
            const request = params.request;
            const urls = params.actions;
            if (callbacks.authenticatorRequest) {
                callbacks.authenticatorRequest({ request, urls });
            }
            ceremonyState = {
                callbacks,
                request,
                statusStrings,
                urls
            };
            return executeRequest(request)
                .then(webauthn.responseToObject);
        })
        .then(finishCeremony);
}

function finishCeremony(response) {
    const callbacks = ceremonyState.callbacks;
    const request = ceremonyState.request;
    const statusStrings = ceremonyState.statusStrings;
    const urls = ceremonyState.urls;

    if (callbacks.serverRequest) {
        callbacks.serverRequest({ urls, request, response });
    }

    return submitResponse(urls.finish, request, response)
        .then(data => {
            return data;
        });
}

function register(username, displayName, credentialNickname, csrfToken,
    requireResidentKey = false,
    getRequest = getRegisterRequest) {
    let request;
    return performCeremony({
        getWebAuthnUrls,
        getRequest: urls => getRequest(urls, username, displayName, credentialNickname, requireResidentKey, csrfToken),
        statusStrings: {
            init: 'Initiating registration ceremony with server...',
            authenticatorRequest: 'Asking authenticators to create credential...',
            success: 'Registration successful.',
        },
        executeRequest: req => {
            request = req;
            return executeRegisterRequest(req);
        }
    })
        .then(data => {
            if (data.registration) {
                if (!data.attestationTrusted) {
                    return rejected("证明不可信！");
                } else {
                    return new Promise((resolve, reject) => resolve(session));
                    // setTimeout(() => {
                    //     $('#sessionToken').val(session.sessionToken);
                    //     $('#register-login-form').submit();
                    // }, 1000);
                }
            }
        })
        .catch((err) => {
            return rejected(err);
        });
}

function getAuthenticateRequest(urls, username) {
    // webauthn/authenticate 接口始终需要加密
    const needEncrypt = true;

    if (needEncrypt) {
        try {
            // 1. 生成随机AES密钥
            const aesKey = generateRandomAesKey();
            currentAesKey = aesKey; // 保存密钥供后续使用

            // 2. 用AES密钥加密请求数据
            const bodyStr = JSON.stringify({ username });
            const encryptedBody = aesEncryptNew(aesKey, bodyStr);

            // 3. 用RSA公钥加密AES密钥
            const encryptedAesKey = rsaEncrypt(RSA_PUBLIC_KEY, aesKey);

            // 4. 发送加密请求并解密响应
            return fetch(urls.authenticate, {
                body: encryptedBody,
                headers: {
                    "X-CSRF-TOKEN": 'csrfToken',
                    "Content-Type": "application/json",
                    "hasCrypto": "true",
                    "privateKey": encryptedAesKey
                },
                method: 'POST',
            })
                .then(response => response.text()) // 先获取文本格式的加密响应
                .then(encryptedResponse => {
                    // 5. 用同一个AES密钥解密响应
                    try {
                        const decryptedData = getUnPackBody(aesKey, encryptedResponse);
                        return decryptedData;
                    } catch (decryptError) {
                        console.error('[WebAuthn解密] 响应解密失败:', decryptError);
                        // 解密失败时尝试直接解析JSON（可能后端没有加密）
                        try {
                            return JSON.parse(encryptedResponse);
                        } catch (jsonError) {
                            throw new Error('响应解密和JSON解析都失败');
                        }
                    }
                })
                .then(updateSession)
                .then(rejectIfNotSuccess);
        } catch (error) {
            console.error('[WebAuthn加密] 加密失败:', error);
            // 加密失败时回退到不加密
            return fetch(urls.authenticate, {
                body: JSON.stringify({ username }),
                headers: {
                    "X-CSRF-TOKEN": 'csrfToken',
                    "Content-Type": "application/json"
                },
                method: 'POST',
            })
                .then(response => response.json())
                .then(updateSession)
                .then(rejectIfNotSuccess);
        }
    } else {
        // 不需要加密的情况
        return fetch(urls.authenticate, {
            body: JSON.stringify({ username }),
            headers: {
                "X-CSRF-TOKEN": 'csrfToken',
                "Content-Type": "application/json"
            },
            method: 'POST',
        })
            .then(response => response.json())
            .then(updateSession)
            .then(rejectIfNotSuccess);
    }
}

function executeAuthenticateRequest(request) {
    return webauthn.getAssertion(request.publicKeyCredentialRequestOptions);
}

function authenticate(username = null, getRequest = getAuthenticateRequest) {
    return performCeremony({
        getWebAuthnUrls,
        getRequest: urls => getRequest(urls, username),
        statusStrings: {
            init: 'Initiating authentication ceremony...',
            authenticatorRequest: 'Asking authenticators to perform assertion...',
            success: 'Authentication successful.',
        },
        executeRequest: executeAuthenticateRequest,
    }).then(data => {
        if (data.registrations) {
            return new Promise((resolve, reject) => resolve(data));
        }
        return data;
    }).catch((err) => {
        return rejected(err);
    });
}

function init() {
    return false;
}

window.onload = init;
