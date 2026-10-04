# Obligation CorDapp with Accounts

This sample demonstrate a full feature cycle of utilizing the AccountSDK. We use the signature Obligation CorDapp as 
the foundation and modify the `issue`, `transfer`, and `settle` functions with Accounts capabilities. 

## Setting up
Go into the project directory and build the project
```
./gradlew clean deployNodes
```
Run the project
```
./build/nodes/runnodes
```
Now, you should have four Corda terminals opened automatically.

## Running the app

Step 1: Create accounts 
At Participant A's terminal
```
flow start CreateNewAccountAndShare acctName: bob6424, node2: ParticipantB, node3: ParticipantC
```
At Participant B's terminal
```
flow start CreateNewAccountAndShare acctName: Nancy3426, node2: ParticipantA, node3: ParticipantC
```
At Participant C's terminal
```
flow start CreateNewAccountAndShare acctName: Peter7548, node2: ParticipantB, node3: ParticipantA
```

Step 2: create IOU from node A, We will have Bob borrow some money from Nancy. 
At Participant A's terminal, we will 1)view all accounts -> 2)get the account ids -> 3)issue IOU
```
flow start ViewAccounts
flow start IOUIssueFlow meID: <XXXXX-XXXXXX-XXXXXXX-Bob's ID>, lenderID: <XXXXX-XXXXXX-XXXXXXX-Nancy's ID>, amount: 20
```

Step 3: We will have Bob to pay some money back
At participant A's terminal, we will first view our account. 
```
flow start ViewIOUByAccount acctname: bob6424
flow start ViewCashBalanceByAccount acctname: bob6424
```
Bob would need some money to pay his loan: (make sure you check the balance is greater than 20 before you settle)
```
flow start MoneyDropFlow acctID: <XXXXX-XXXXXX-XXXXXXX-Bob's ID>
```
Now Bob will settle part of his loan
```
flow start IOUSettleFlow linearId: <XXXXX-XXXXXX-XXXXXXX-IOU-ID>, meID: <XXXXX-XXXXXX-XXXXXXX-Bob's ID>, settleAmount: 5
```

Step 4: We will have the lender of the loan(Nancy) transfer the loan to a new holder Peter
At Participant B's terminal 
```
flow start IOUTransferFlow linearId: <XXXXX-XXXXXX-XXXXXXX-IOU-ID>, meID: <XXXXX-XXXXXX-XXXXXXX-Nancy's ID>, newLenderID: <XXXXX-XXXXXX-XXXXXXX-Peter's ID>
```

Step 5: As the ownership of the state changes, the new owner would need to update the rest of the participant list of its new ownership.
At Participant C's terminal 
```
flow start SyncIOU linearId: <XXXXX-XXXXXX-XXXXXXX-IOU-ID>, party: ParticipantA

```
Step 6: Bob settles the rest of the loan with Peter
At participant A's terminal
```
flow start IOUSettleFlow linearId: <XXXXX-XXXXXX-XXXXXXX-IOU-ID>, meID: <XXXXX-XXXXXX-XXXXXXX-Bob's ID>, settleAmount: 15
```
Thus, it completes the full cycle. 
We can use the two query flows to check each account's holdings, by changing the account name.  
```
flow start ViewIOUByAccount acctname: bob6424
flow start ViewCashBalanceByAccount acctname: bob6424
```


Purpose
obligation-accounts demonstrates an obligation/IOU application implemented using Corda Accounts.

The application models an IOU between parties and provides flows for issuing, transferring, settling, and synchronizing IOUs.

Structure
obligation-accounts/
├── contracts/
│   └── src/
│       ├── main/kotlin/
│       │   └── net/corda/samples/obligation/
│       │       ├── contract/
│       │       │   └── IOUContract.kt
│       │       └── states/
│       │           └── IOUState.kt
│       └── test/kotlin/
│           └── net/corda/samples/obligation/
│               └── IOUStateTests.kt
│
└── workflows/
    └── src/
        ├── main/kotlin/
        │   └── net/corda/samples/obligation/
        │       ├── accountUtil/
        │       └── flows/
        │           ├── IOUIssueFLow.kt
        │           ├── IOUSettleFlow.kt
        │           ├── IOUTransferFlow.kt
        │           ├── MoneyDropFlow.kt
        │           └── SyncIOU.kt
        │
        └── integrationTest/

Contract layer
IOUState.kt represents an IOU on the ledger.

IOUContract.kt defines the transaction rules governing the IOU.

The contract layer is responsible for validating transactions independently of the flow implementation.

Workflow layer
The main workflows include:

IOUIssueFLow
Creates a new IOU.