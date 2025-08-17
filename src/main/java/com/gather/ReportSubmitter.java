package com.gather;

import io.github.cdimascio.dotenv.Dotenv;
import org.web3j.abi.DefaultFunctionEncoder;
import org.web3j.abi.FunctionEncoder;
import org.web3j.abi.datatypes.*;
import org.web3j.abi.datatypes.generated.Int256;
import org.web3j.abi.datatypes.generated.StaticArray2;
import org.web3j.abi.datatypes.generated.StaticArray6;
import org.web3j.abi.datatypes.generated.Uint256;
import org.web3j.crypto.*;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.DefaultBlockParameterName;
import org.web3j.protocol.core.methods.response.*;
import org.web3j.protocol.http.HttpService;
import org.web3j.utils.Numeric;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * ReportSubmitter - Java implementation for submitting air quality reports to smart contract
 * 
 * This implementation replicates the JavaScript web3.eth.accounts.sign() behavior
 * to ensure signature compatibility with smart contracts using OpenZeppelin's ECDSA.recover()
 */
public class ReportSubmitter {
    
    // Load environment variables
    private static final Dotenv dotenv = Dotenv.configure().ignoreIfMissing().load();
    
    // Environment variables
    private static String PRIVATE_KEY = dotenv.get("PRIVATE_KEY");
    private static String SEI_RPC = dotenv.get("SEI_RPC");
    private static String CONTRACT_ADDRESS = dotenv.get("CONTRACT_ADDRESS");
    
    // Sample data - replace with your actual sensor data
    private static final String SERVER_ID = "linux-0000-0008";
    private static final String USER_CODE = "abc8-ece8-acde-12de";
    private static final long TIMESTAMP = System.currentTimeMillis();
    private static final BigInteger[] SENSORS = {
        new BigInteger("12"), new BigInteger("270"), new BigInteger("13"),
        new BigInteger("633"), new BigInteger("633"), new BigInteger("71")
    };
    private static final BigInteger[] LOCATION = {
        new BigInteger("1132344449"), new BigInteger("362311116")
    };

    public static void main(String[] args) {
        try {
            // Setup
            if (!PRIVATE_KEY.startsWith("0x")) {
                PRIVATE_KEY = "0x" + PRIVATE_KEY;
            }

            Web3j web3j = Web3j.build(new HttpService(SEI_RPC));
            Credentials credentials = Credentials.create(PRIVATE_KEY);
            
            System.out.println("=== AIR QUALITY REPORT SUBMISSION ===");
            System.out.println("Account: " + credentials.getAddress());
            System.out.println("Contract: " + CONTRACT_ADDRESS);
            System.out.println("Data: " + Arrays.toString(SENSORS));
            
            // Step 1: ABI encode the payload
            String encodedPayload = abiEncodePayload();
            
            // Step 2: Hash the payload
            byte[] payloadHash = keccak256Hash(encodedPayload);
            System.out.println("Payload hash: " + Numeric.toHexString(payloadHash));
            
            // Step 3: Sign with Ethereum message prefix (critical for smart contract compatibility)
            String signature = signWithEthereumPrefix(payloadHash, credentials);
            System.out.println("Signature: " + signature);
            
            // Step 4: Submit transaction
            submitReport(web3j, credentials, signature);
            
        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * Signs the payload hash with Ethereum message prefix
     * This replicates JavaScript web3.eth.accounts.sign() behavior
     */
    private static String signWithEthereumPrefix(byte[] hash, Credentials credentials) {
        try {
            // Create Ethereum signed message hash: keccak256("\x19Ethereum Signed Message:\n32" + hash)
            String prefix = "\u0019Ethereum Signed Message:\n32";
            byte[] prefixBytes = prefix.getBytes();
            byte[] prefixedMessage = new byte[prefixBytes.length + hash.length];
            System.arraycopy(prefixBytes, 0, prefixedMessage, 0, prefixBytes.length);
            System.arraycopy(hash, 0, prefixedMessage, prefixBytes.length, hash.length);
            
            byte[] prefixedHash = Hash.sha3(prefixedMessage);
            
            // Sign the prefixed hash
            ECKeyPair keyPair = credentials.getEcKeyPair();
            ECDSASignature ecdsaSignature = keyPair.sign(prefixedHash);
            
            // Calculate recovery ID
            int recoveryId = -1;
            for (int i = 0; i < 4; i++) {
                BigInteger recoveredPublicKey = Sign.recoverFromSignature(i, ecdsaSignature, prefixedHash);
                if (recoveredPublicKey != null && recoveredPublicKey.equals(keyPair.getPublicKey())) {
                    recoveryId = i;
                    break;
                }
            }
            
            if (recoveryId == -1) {
                throw new RuntimeException("Unable to calculate recovery ID");
            }
            
            // Create signature in Ethereum format (v = recoveryId + 27)
            byte[] signature = new byte[65];
            System.arraycopy(Numeric.toBytesPadded(ecdsaSignature.r, 32), 0, signature, 0, 32);  // r
            System.arraycopy(Numeric.toBytesPadded(ecdsaSignature.s, 32), 0, signature, 32, 32); // s
            signature[64] = (byte) (recoveryId + 27); // v in Ethereum format (27/28)
            
            return Numeric.toHexString(signature);
            
        } catch (Exception e) {
            throw new RuntimeException("Signing failed: " + e.getMessage(), e);
        }
    }
    
    /**
     * Submits the report to the smart contract
     */
    private static void submitReport(Web3j web3j, Credentials credentials, String signature) {
        try {
            System.out.println("\n=== SUBMITTING TRANSACTION ===");
            
            // Create function call parameters
            List<Type> parameters = Arrays.asList(
                new Utf8String(SERVER_ID),
                new Utf8String(USER_CODE),
                new Uint256(TIMESTAMP),
                new StaticArray6<>(Uint256.class, Arrays.stream(SENSORS).map(Uint256::new).collect(Collectors.toList())),
                new StaticArray2<>(Int256.class, Arrays.stream(LOCATION).map(Int256::new).collect(Collectors.toList())),
                new DynamicBytes(Numeric.hexStringToByteArray(signature))
            );
            
            Function function = new Function("submitReport", parameters, new ArrayList<>());
            String encodedFunction = FunctionEncoder.encode(function);
            
            // Get transaction parameters
            BigInteger nonce = web3j.ethGetTransactionCount(credentials.getAddress(), DefaultBlockParameterName.LATEST)
                .send().getTransactionCount();
            BigInteger gasPrice = web3j.ethGasPrice().send().getGasPrice();
            BigInteger gasLimit = BigInteger.valueOf(500000);
            
            // Create and send transaction
            RawTransaction rawTransaction = RawTransaction.createTransaction(
                nonce, gasPrice, gasLimit, CONTRACT_ADDRESS, encodedFunction);
            
            byte[] signedMessage = TransactionEncoder.signMessage(rawTransaction, credentials);
            String hexValue = Numeric.toHexString(signedMessage);
            
            EthSendTransaction ethSendTransaction = web3j.ethSendRawTransaction(hexValue).send();
            
            if (ethSendTransaction.hasError()) {
                throw new RuntimeException("Transaction failed: " + ethSendTransaction.getError().getMessage());
            }
            
            String transactionHash = ethSendTransaction.getTransactionHash();
            System.out.println("Transaction hash: " + transactionHash);
            
            // Wait for confirmation
            waitForConfirmation(web3j, transactionHash);
            
        } catch (Exception e) {
            throw new RuntimeException("Transaction submission failed: " + e.getMessage(), e);
        }
    }
    
    /**
     * Waits for transaction confirmation
     */
    private static void waitForConfirmation(Web3j web3j, String transactionHash) {
        try {
            System.out.println("Waiting for confirmation...");
            
            for (int i = 0; i < 30; i++) {
                Optional<TransactionReceipt> receipt = web3j.ethGetTransactionReceipt(transactionHash)
                    .send().getTransactionReceipt();
                
                if (receipt.isPresent()) {
                    TransactionReceipt txReceipt = receipt.get();
                    
                    if ("0x1".equals(txReceipt.getStatus())) {
                        System.out.println("✅ SUCCESS! Report submitted successfully!");
                        System.out.println("   Block: " + txReceipt.getBlockNumber());
                        System.out.println("   Gas used: " + txReceipt.getGasUsed());
                        return;
                    } else {
                        System.out.println("❌ Transaction reverted");
                        throw new RuntimeException("Transaction was reverted");
                    }
                }
                
                System.out.print(".");
                Thread.sleep(2000);
            }
            
            throw new RuntimeException("Transaction confirmation timeout");
            
        } catch (Exception e) {
            throw new RuntimeException("Confirmation failed: " + e.getMessage(), e);
        }
    }
    
    /**
     * ABI encodes the payload parameters
     */
    private static String abiEncodePayload() {
        try {
            List<Type> parameters = Arrays.asList(
                new Utf8String(SERVER_ID),
                new Utf8String(USER_CODE),
                new Uint256(TIMESTAMP),
                new StaticArray6<>(Uint256.class, Arrays.stream(SENSORS).map(Uint256::new).collect(Collectors.toList())),
                new StaticArray2<>(Int256.class, Arrays.stream(LOCATION).map(Int256::new).collect(Collectors.toList()))
            );
            
            DefaultFunctionEncoder encoder = new DefaultFunctionEncoder();
            String encoded = encoder.encodeParameters(parameters);
            
            if (!encoded.startsWith("0x")) {
                encoded = "0x" + encoded;
            }
            
            return encoded;
            
        } catch (Exception e) {
            throw new RuntimeException("ABI encoding failed: " + e.getMessage(), e);
        }
    }
    
    /**
     * Calculates keccak256 hash of the encoded payload
     */
    private static byte[] keccak256Hash(String encodedPayload) {
        try {
            byte[] payloadBytes = Numeric.hexStringToByteArray(encodedPayload);
            return Hash.sha3(payloadBytes);
        } catch (Exception e) {
            throw new RuntimeException("Keccak256 hashing failed: " + e.getMessage(), e);
        }
    }
}
