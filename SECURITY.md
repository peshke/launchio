# Security

Launchio is a home screen installed on TVs over ADB, so a security bug here can reach people's
living rooms. Please report anything you find privately, not in a public issue.

## How to report

Use GitHub's private vulnerability reporting: open the
[Security tab](https://github.com/peshke/launchio/security) and choose **Report a vulnerability**.
Only the maintainer sees the report.

Please include what is affected (the launcher, a script in `launcher/` or `tools/`, a theme), the
steps to reproduce it, and what an attacker could do with it.

## What counts

- The launcher (`io.github.peshke.launchio`) doing something it shouldn't: exposing data to other
  apps, reaching places on the network it has no reason to, or being made to open something it
  shouldn't.
- The scripts (`launcher/build-and-install.sh`, `tools/tv.py`, `tools/theme.sh`) running commands on
  a device or computer they weren't pointed at, or mishandling paths and input.
- Anything in a pull request that runs code on a contributor's or maintainer's machine.

ADB itself is powerful by design. Turning on USB or network debugging lets any computer on your
network ask to connect, and the TV asks you to allow it. Turn debugging off when you're done.

## What happens next

You'll get a reply within a few days. Fixes go into `main` with credit to you, unless you'd rather
stay anonymous. Please give a fix a reasonable chance to ship before you publish details.
