package com.farmcraft.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.tree.CommandNode;
import net.minecraft.commands.CommandSourceStack;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Collection;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests to ensure FarmCraft commands are properly registered
 * and available in the command dispatcher.
 */
public class CommandRegistrationTest {
    private CommandDispatcher<CommandSourceStack> dispatcher;

    @BeforeEach
    public void setUp() {
        dispatcher = new CommandDispatcher<>();
        FarmCraftCommand.register(dispatcher);
    }

    @Test
    public void testMainCommandExists() {
        CommandNode<CommandSourceStack> farmcraftNode = dispatcher.getRoot().getChild("farmcraft");

        assertNotNull(farmcraftNode,
                "Main /farmcraft command must be registered");
    }

    @Test
    public void testAllSubcommandsExist() {
        CommandNode<CommandSourceStack> farmcraftNode = dispatcher.getRoot().getChild("farmcraft");

        assertNotNull(farmcraftNode, "Main command must exist before checking subcommands");

        // Check each expected subcommand
        String[] expectedSubcommands = { "guide", "status", "help", "ask", "topics" };

        for (String subcommand : expectedSubcommands) {
            CommandNode<CommandSourceStack> subNode = farmcraftNode.getChild(subcommand);
            assertNotNull(subNode,
                    String.format("Subcommand '%s' must be registered under /farmcraft", subcommand));
        }
    }

    @Test
    public void testHelpCommandHasTopicArgument() {
        CommandNode<CommandSourceStack> farmcraftNode = dispatcher.getRoot().getChild("farmcraft");
        CommandNode<CommandSourceStack> helpNode = farmcraftNode.getChild("help");

        assertNotNull(helpNode, "Help command must exist");

        // Help should have a "topic" argument child
        Collection<CommandNode<CommandSourceStack>> children = helpNode.getChildren();
        assertTrue(children.size() > 0,
                "Help command should have argument nodes");
    }

    @Test
    public void testAskCommandHasQuestionArgument() {
        CommandNode<CommandSourceStack> farmcraftNode = dispatcher.getRoot().getChild("farmcraft");
        CommandNode<CommandSourceStack> askNode = farmcraftNode.getChild("ask");

        assertNotNull(askNode, "Ask command must exist");

        // Ask should have a "question" argument child
        Collection<CommandNode<CommandSourceStack>> children = askNode.getChildren();
        assertTrue(children.size() > 0,
                "Ask command should have argument nodes");
    }

    @Test
    public void testMainCommandHasExecutor() {
        CommandNode<CommandSourceStack> farmcraftNode = dispatcher.getRoot().getChild("farmcraft");

        assertNotNull(farmcraftNode.getCommand(),
                "Main /farmcraft command should have an executor (defaults to guide)");
    }

    @Test
    public void testAllSubcommandsHaveExecutors() {
        CommandNode<CommandSourceStack> farmcraftNode = dispatcher.getRoot().getChild("farmcraft");

        String[] executableCommands = { "guide", "status", "topics" };

        for (String command : executableCommands) {
            CommandNode<CommandSourceStack> node = farmcraftNode.getChild(command);
            assertNotNull(node.getCommand(),
                    String.format("Command '%s' should have an executor", command));
        }
    }

    @Test
    public void testNoUnexpectedCommands() {
        CommandNode<CommandSourceStack> farmcraftNode = dispatcher.getRoot().getChild("farmcraft");

        Collection<CommandNode<CommandSourceStack>> children = farmcraftNode.getChildren();

        // We expect exactly 5 subcommands
        assertEquals(5, children.size(),
                "FarmCraft should have exactly 5 subcommands: guide, status, help, ask, topics");
    }

    @Test
    public void testCommandStructureIntegrity() {
        // Ensure the entire command tree is properly formed
        CommandNode<CommandSourceStack> root = dispatcher.getRoot();

        assertNotNull(root, "Command root must exist");
        assertTrue(root.getChildren().size() > 0,
                "Root should have at least one command (farmcraft)");

        CommandNode<CommandSourceStack> farmcraft = root.getChild("farmcraft");
        assertNotNull(farmcraft, "Farmcraft command must be in root");
        assertTrue(farmcraft.getChildren().size() > 0,
                "Farmcraft should have subcommands");
    }
}
