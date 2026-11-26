<p align="center">
  <img src="logo.svg" width="96" alt="MomoBook logo">
</p>

<h1 align="center">MomoBook</h1>

<p align="center">
  <a href="https://github.com/henryhale/momobook/actions/workflows/release.yml"><img src="https://github.com/henryhale/momobook/actions/workflows/release.yml/badge.svg" alt="Release build"></a>
  <a href="https://github.com/henryhale/momobook/releases/latest"><img src="https://img.shields.io/github/v/release/henryhale/momobook?label=download" alt="Latest release"></a>
  <img src="https://img.shields.io/badge/platform-Android%208%2B-3DDC84?logo=android&logoColor=white" alt="Android 8+">
  <img src="https://img.shields.io/badge/Kotlin-Jetpack%20Compose-7F52FF?logo=kotlin&logoColor=white" alt="Kotlin + Jetpack Compose">
  <a href="LICENSE"><img src="https://img.shields.io/github/license/henryhale/momobook" alt="License: Apache 2.0"></a>
</p>

<p align="center">Offline journal and money tracker for Android, built from your messages.</p>

## Overview

For millions of people, mobile money and bank alerts are the main record of where their money goes, but that record is just a stream of SMS messages. They are hard to read back, easy to delete, and say nothing about your spending as a whole.

MomoBook reads those messages on your phone and turns them into a proper money journal:

- **Automatic journal:** deposits, payments, transfers, withdrawals, airtime, bundles and fees are recorded as debit/credit entries the moment an SMS arrives.
- **History that lasts:** every message is copied into the journal, so your records stay even if the SMS is deleted.
- **Insights:** spending by category, top payees, top sources and agents, cash flow, fees and balance trends, by range or by month.
- **Calendar:** income and expenses for each day, with a tap-through summary of that day.
- **Every wallet, dual SIM:** each wallet (per network and SIM, or per bank account) is tracked separately, with a combined view.
- **Private by design:** everything is stored on your phone. Nothing is uploaded, and balances are hidden until you choose to show them.

## Supported messages

| Channel | Type |
| --- | --- |
| MTN Mobile Money | Mobile money |
| Airtel Money | Mobile money |
| Centenary Bank | Bank alerts |
| DTB (Diamond Trust Bank) | Transfers, fixed deposits, alerts |
| Stanbic Bank | Bank alerts |

Message formats are currently those used in Uganda (UGX). More channels and countries are planned. To request one, [open an issue](https://github.com/henryhale/momobook/issues) with a few sample messages (remove any personal details first).

## Download

Get the latest APK from [Releases](https://github.com/henryhale/momobook/releases/latest).

## License

[Apache 2.0](LICENSE). MomoBook is an independent project and is not affiliated with any mobile network or bank.
