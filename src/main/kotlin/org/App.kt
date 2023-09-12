package org

import org.jub.kotlin.hometask4.Application

import java.io.File
import java.util.*
import java.util.concurrent.*

internal class App(
    private val resultsFile: File,
    private val tasks: List<Callable<out Any>?>,
) : Application {
    private var acceptingTasks = true
    private val threadPool: ExecutorService = Executors.newFixedThreadPool(NUMBER_OF_THREADS)

    override fun run() {
        println("Starting…")
        while (!threadPool.isShutdown) {
            val input: String? = readlnOrNull()?.trim()?.lowercase(Locale.getDefault())
            if (input.isNullOrEmpty() || input == "finish") {
                acceptingTasks = false
                break
            } else {
                parseCommand(input)
            }
        }
    }

    override fun waitToFinish() {
        threadPool.shutdown()
        threadPool.awaitTermination(Long.MAX_VALUE, TimeUnit.NANOSECONDS)
    }

    private fun parseCommand(command: String) {
        val parts: List<String> = command.split(" ", limit = 2)
        val cmd: String = parts[0].trim().lowercase(Locale.getDefault())
        val args: String = parts.getOrNull(1)?.trim() ?: ""
        if (cmd in listOf("clean", "help", "get") && args.isNotEmpty()) {
            println("Invalid command. Use 'help' for available commands.")
            return
        }
        executeCommand(cmd, args)
    }

    private fun executeCommand(cmd: String, args: String = "") {
        when (cmd) {
            "get" -> displayLastResult()
            "clean" -> cleanResultsFile()
            "help" -> displayHelp()
            "finish" -> {
                when (args) {
                    "grace" -> finishGracefully()
                    "force" -> finishForcefully()
                    else -> println("Invalid finish mode. Use 'grace' or 'force'.")
                }
            }

            "task" -> executeTask(args)
            else -> println("Invalid command. Use 'help' for available commands.")
        }
    }

    private fun executeTask(taskInfo: String) {
        if (!acceptingTasks) {
            println("Task submission is not allowed. Use 'finish' to stop accepting new tasks.")
            return
        }

        val parts: List<String> = taskInfo.split(" ", limit = 2)
        if (parts.size != 2) {
            println("Invalid task format. Use 'task NAME X'.")
            return
        }
        val (name, indexString) = parts

        val index: Int? = indexString.toIntOrNull()
        if (index == null) {
            println("Invalid task index. Use an integer for the task index.")
            return
        }

        val task = tasks.getOrNull(index)
        if (task == null) {
            println("Invalid task index. Available indexes: 0-${tasks.size - 1}.")
            return
        }
        threadPool.execute {
            try {
                val taskResult = task.call().toString()
                println("$taskResult [$name]")

                val resultRecord = "$name: $taskResult"
                synchronized(resultsFile) {
                    resultsFile.appendText("$resultRecord${System.lineSeparator()}")
                }
            } catch (e: Exception) {
                println("Error while executing task: ${e.message}")
            }
        }
    }

    private fun displayLastResult() {
        if (!resultsFile.exists() || resultsFile.isDirectory) {
            println("File does not exist or is a directory.")
            return
        }
        val lastResult = resultsFile.readLines().lastOrNull()
        if (lastResult == null) {
            println("No results available.")
            return
        }
        val parts: List<String> = lastResult.split(":")
        if (parts.size == 2) {
            val (name, result) = parts
            println("$result [$name]")
            return
        } else {
            println("No results available.")
        }
    }

    private fun finishGracefully() {
        acceptingTasks = false
        threadPool.shutdown()
    }

    private fun finishForcefully() {
        acceptingTasks = false
        threadPool.shutdown()
        val blockingQueue = (threadPool as ThreadPoolExecutor).queue
        while (blockingQueue.size != 0) {
            blockingQueue.poll()
        }
    }

    private fun cleanResultsFile() {
        resultsFile.writeText("")
        println("Results file cleaned.")
    }

    private fun displayHelp() {
        println(
            """
            Available commands:
            task NAME X: Execute task X, name it NAME, and write the result to the results file.
            get: Output the last result and its name to the console.
            finish grace: Stop accepting new tasks, finish all pending tasks, and stop the application.
            finish force: Stop the application without waiting for pending tasks to finish.
            clean: Clean the results file.
            help: Output guidelines to the console.
            """
        )
    }

    companion object {
        const val NUMBER_OF_THREADS = 6
    }
}
