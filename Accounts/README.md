
Overview
This repository contains several Kotlin-based CorDapps demonstrating different uses of Corda, including obligations, supply-chain workflows, Corda Accounts, and a distributed Tic-Tac-Toe application.

The repository is organized as a collection of largely independent Corda applications:

obligation-accounts — demonstrates IOU/obligation management using Corda Accounts.

supplychain — demonstrates a multi-party supply-chain workflow involving cargo, invoices, payments, shipping requests, and internal messages.

sharestatewithaccount — demonstrates sharing application state with Corda Accounts, including scenarios involving accounts that are not direct state participants.

tictacthor — demonstrates a Corda-based Tic-Tac-Toe application with a web client.

Each application generally follows Corda's separation between:

Contracts and states — the shared ledger model and transaction validation rules.

Workflows — the flows that construct, sign, distribute, and finalize transactions.

Tests — contract, state, flow, and integration tests.

Configuration — logging and Gradle/build configuration.




## Accounts CorDapp Samples 

This folder features Corda Accounts sample projects. Learn more about [Accounts](https://training.corda.net/libraries/accounts-lib/).

### [Supply Chain](./supplychain):

This CorDapp mimics a supply chain transaction, where the deal is incorporated among different teams in the companies on both side of the trade.  
<p align="center">
  <img src="./supplychain/Business%20Flow.png" alt="Corda" width="700">
</p>

### [Tic Tac Thor](./tictacthor):

This CorDapp recreates the game of Tic Tac Toe via Corda. It primarily demonstrates how you can have LinearState transactions between cross-node accounts.  
<p align="center">
  <img src="https://upload.wikimedia.org/wikipedia/commons/thumb/3/32/Tic_tac_toe.svg/1024px-Tic_tac_toe.svg.png" alt="Corda" width="200">
</p>


.
├── README.md
├── constants.properties
│
├── obligation-accounts/
│   ├── contracts/
│   ├── workflows/
│   ├── config/
│   ├── gradle/
│   ├── lib/
│   └── build.gradle
│
├── sharestatewithaccount/
│   ├── contracts/
│   ├── workflows/
│   ├── config/
│   ├── gradle/
│   ├── lib/
│   └── build.gradle
│
├── supplychain/
│   ├── contracts/
│   ├── workflows/
│   ├── config/
│   ├── gradle/
│   ├── lib/
│   └── build.gradle
│
└── tictacthor/
    ├── clients/
    ├── contracts/
    ├── workflows/
    ├── config/
    ├── gradle/
    ├── lib/
    └── build.gradle

