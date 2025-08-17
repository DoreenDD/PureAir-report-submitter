# Web3j Smart Contract Integration

A Java implementation for submitting air quality reports to Ethereum smart contracts, with full compatibility with JavaScript Web3 libraries.

## Overview

This project demonstrates how to properly integrate Java Web3j with smart contracts that use OpenZeppelin's ECDSA signature verification. The implementation replicates JavaScript `web3.eth.accounts.sign()` behavior to ensure signature compatibility.

## Prerequisites

- Java 11 or higher
- Maven 3.6+
- Access to an Ethereum-compatible RPC endpoint
- Private key for transaction signing

## Setup

1. **Clone the repository**
```bash
git clone <repository-url>
cd web3j-gather
```

2. **Install dependencies**
```bash
mvn clean install
```

3. **Configure environment variables**
Create a `.env` file in the project root:
```env
PRIVATE_KEY=your_private_key_here
SEI_RPC=https://evm-rpc-testnet.sei-apis.com
CONTRACT_ADDRESS=0x2562C7761d7431b7B6A2D04Aa79c4409A8612307
```

## Usage

### Basic Usage
```bash
mvn clean compile exec:java -Dexec.mainClass="com.gather.ReportSubmitter"
```

### Customizing Data
Modify the constants in `ReportSubmitter.java`:
```java
private static final String SERVER_ID = "your-server-id";
private static final String USER_CODE = "your-user-code";
private static final BigInteger[] SENSORS = {
    new BigInteger("sensor1"), new BigInteger("sensor2"), // ... 6 values
};
private static final BigInteger[] LOCATION = {
    new BigInteger("latitude"), new BigInteger("longitude")
};
```

## Code Structure

### Main Components

#### 1. **ReportSubmitter.java**
Main class containing the complete workflow:
- ABI encoding of parameters
- Keccak256 hashing
- Ethereum-compatible signature generation
- Transaction submission and confirmation

#### 2. **Key Methods**

**`signWithEthereumPrefix()`**
```java
// Creates signature compatible with smart contract's ECDSA.recover()
private static String signWithEthereumPrefix(byte[] hash, Credentials credentials)
```
- Adds Ethereum message prefix: `"\x19Ethereum Signed Message:\n32"`
- Signs the prefixed hash (not the original hash)
- Returns signature in Ethereum v format (27/28)

**`abiEncodePayload()`**
```java
// Encodes function parameters according to smart contract ABI
private static String abiEncodePayload()
```
- Uses exact parameter types from smart contract
- Returns hex-encoded ABI data

**`submitReport()`**
```java
// Submits transaction to smart contract
private static void submitReport(Web3j web3j, Credentials credentials, String signature)
```
- Creates function call with proper parameters
- Handles transaction signing and submission
- Waits for confirmation

### Workflow

1. **ABI Encoding**: Parameters are encoded according to smart contract ABI
   ```
   ["string", "string", "uint256", "uint256[6]", "int256[2]"]
   ```

2. **Payload Hashing**: Keccak256 hash of encoded parameters
   ```java
   byte[] payloadHash = Hash.sha3(payloadBytes);
   ```

3. **Ethereum Signature**: Signs prefixed hash for smart contract compatibility
   ```java
   String prefix = "\u0019Ethereum Signed Message:\n32";
   byte[] prefixedHash = Hash.sha3(prefix.getBytes() + payloadHash);
   ```

4. **Transaction Submission**: Calls `submitReport()` function with signature
   ```solidity
   function submitReport(
       string memory serverId,
       string memory userCode, 
       uint256 timestamp,
       uint256[6] memory sensors,
       int256[2] memory location,
       bytes memory signature
   )
   ```

## Smart Contract Compatibility

### Signature Verification
The smart contract uses OpenZeppelin's ECDSA library:
```solidity
address signer = ECDSA.recover(toEthSignedMessageHash(payloadHash), signature);
```

### Key Requirements
- Signature must be created from **prefixed hash** (not raw hash)
- Signature format must use **Ethereum v values** (27/28, not 0/1)
- ABI encoding must match **exact parameter types**

## Troubleshooting

### Common Issues

**Low Gas Usage (~31,000-37,000)**
- Indicates early transaction revert
- Usually caused by signature verification failure
- Check that Ethereum message prefix is being added

**Gas Estimation Failures**
- Often indicates the transaction will revert
- Verify ABI encoding matches smart contract exactly
- Ensure signature is created correctly

**"Already submitted" Errors**
- Smart contract prevents duplicate submissions
- Change timestamp or other parameters for testing

### Expected Behavior
- **Successful transaction**: ~326,000 gas usage
- **Failed signature verification**: ~31,000-37,000 gas usage
- **Parameter validation failure**: ~50,000-100,000 gas usage

## Dependencies

```xml
<dependency>
    <groupId>org.web3j</groupId>
    <artifactId>core</artifactId>
    <version>4.9.8</version>
</dependency>
<dependency>
    <groupId>io.github.cdimascio</groupId>
    <artifactId>dotenv-java</artifactId>
    <version>3.0.0</version>
</dependency>
```

## Security Notes

- Never commit private keys to version control
- Use environment variables for sensitive data
- Test on testnets before mainnet deployment
- Validate all input parameters before signing

## License

MIT License - see LICENSE file for details