
# Java Shell

A Unix-style command-line shell implemented in Java from
scratch, exploring core operating system concepts such as
process management, command interpretation, inter-process
communication and I/O redirection.

[![Java](https://img.shields.io/badge/Java-ED8B00?style=for-the-badge&logo=openjdk&logoColor=white)](https://www.java.com/)
![Maven](https://img.shields.io/badge/Apache_Maven-C71A36?style=for-the-badge&logo=apachemaven&logoColor=white)
![Linux](https://img.shields.io/badge/Linux-FCC624?style=for-the-badge&logo=linux&logoColor=black)

## Features

- Interactive command-line interface (REPL)
- Built-in shell commands
- External program execution
- PATH-based executable lookup
- Directory navigation
- Single and double quote handling
- Escape character processing
- Standard output and error redirection
- Append-mode file redirection
- Multi-command pipelines
- Multithreaded stream handling
- Background process execution
- Background job tracking

## Supported Commands

| Command | Description |
|---------|-------------|
| `echo` | Display text in the terminal |
| `pwd` | Print the current working directory |
| `cd` | Change the working directory |
| `type` | Identify built-in and external commands |
| `jobs` | Display background jobs |
| `exit` | Terminate the shell |

External executable programs are located using the
system's PATH environment variable.

## Shell Operators

| Operator | Description |
|----------|-------------|
| `>` | Redirect standard output |
| `>>` | Append standard output |
| `2>` | Redirect standard error |
| `2>>` | Append standard error |
| `\|` | Connect commands through a pipeline |
| `&` | Execute a process in the background |

## Architecture

The shell processes commands through the following stages:

```text
User Input
    |
    v
Command Parser
    |
    v
Command Dispatcher
    |
    +-- Built-in Commands
    |
    +-- External Process Execution
    |
    +-- Pipeline Execution
    |
    +-- Background Job Management
    |
    v
Output / File Redirection
```

The implementation uses Java's ProcessBuilder API for
external process execution and Java threads for transferring
data between processes in pipelines.

## Technology Stack

- Java
- Apache Maven
- Java ProcessBuilder API
- Java Multithreading
- Java I/O and NIO APIs
- Unix/Linux Shell Concepts

## Getting Started

### Prerequisites

- JDK 26
- Apache Maven
- Linux, macOS or WSL

### Installation

Clone the repository:

```bash
git clone https://github.com/Darshan-J-07/java-shell.git
cd java-shell
```

### Running the Shell

```bash
bash your_program.sh
```

## Example Commands

Built-in commands:

```bash
echo Hello World
pwd
cd /tmp
type echo
```

Output redirection:

```bash
echo Hello > output.txt
echo World >> output.txt
```

Command pipelines:

```bash
echo hello | wc -c
cat output.txt | sort
```

Background processes:

```bash
sleep 10 &
jobs
```

Exit the shell:

```bash
exit
```

## Project Structure

```text
java-shell/
├── .codecrafters/
├── src/
│   └── main/
│       └── java/
│           └── Main.java
├── .gitignore
├── codecrafters.yml
├── pom.xml
├── your_program.sh
└── README.md
```

## Concepts Explored

- Operating system fundamentals
- Process creation and lifecycle management
- Inter-process communication
- Concurrent execution and multithreading
- Command parsing and tokenization
- File handling and stream management
- Environment variables
- System-level programming

## Future Improvements

- Command history
- Tab autocompletion
- Improved signal handling
- Enhanced error handling
- Modular command parser
- Automated unit and integration tests
- Expanded POSIX compatibility

## Acknowledgements

This project was developed as part of the
[Build Your Own Shell](https://app.codecrafters.io/courses/shell/overview)
challenge by CodeCrafters.

[![Progress](https://backend.codecrafters.io/progress/shell/fc020e95-5e79-4ae7-aebf-fb25c3759329)](https://app.codecrafters.io/users/darshan1117?r=2qF)

## Author

**J. Darshan**

[GitHub](https://github.com/Darshan-J-07) |
[LinkedIn](https://www.linkedin.com/in/darshan-j07)
