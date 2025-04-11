# .bash_profile
umask 0027

# Get the aliases and functions
if [ -f ~/.bashrc ]; then
        . ~/.bashrc
fi

# User specific environment and startup programs

BASH_ENV=$HOME/.bashrc
USERNAME="zimbra"

export USERNAME BASH_ENV 

export LANG=C
