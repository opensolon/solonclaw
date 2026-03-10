#!/usr/bin/expect -f
set timeout 300
spawn scp -o StrictHostKeyChecking=no target/solonclaw.jar root@156.225.28.65:/root/solonclaw/
expect {
    "password:" {
        send "qrmwNIKZ7693\r"
        exp_continue
    }
    eof
}
