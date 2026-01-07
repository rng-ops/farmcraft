package com.farmcraft.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Integration tests for FarmCraft commands.
 * These tests ensure all commands are properly registered and functional.
 */
public class CommandIntegrationTest {
    private CommandDispatcher<CommandSourceStack> dispatcher;
    private CommandSourceStack mockSource;
    private ArgumentCaptor<Supplier<Component>> messageCaptor;

    @BeforeEach
    public void setUp() {
        // Create a fresh dispatcher for each test
        dispatcher = new CommandDispatcher<>();
        
        // Register the FarmCraft commands
        FarmCraftCommand.register(dispatcher);
        
        // Create mock source
        mockSource = mock(CommandSourceStack.class);
        ServerPlayer mockPlayer = mock(ServerPlayer.class);
        
        when(mockSource.getPlayerOrException()).thenReturn(mockPlayer);
        when(mockSource.getPosition()).thenReturn(Vec3.ZERO);
        when(mockSource.getRotation()).thenReturn(Vec2.ZERO);
        when(mockSource.hasPermission(anyInt())).thenReturn(true);
        
        // Capture messages sent by commands
        messageCaptor = ArgumentCaptor.forClass(Supplier.class);
    }

    @Test
    public void testCommandsAreRegistered() {
        // Verify the main farmcraft command is registered
        assertNotNull(dispatcher.getRoot().getChild("farmcraft"), 
            "Main /farmcraft command should be registered");
        
        // Verify subcommands are registered
        var farmcraftNode = dispatcher.getRoot().getChild("farmcraft");
        assertNotNull(farmcraftNode.getChild("guide"), 
            "/farmcraft guide should be registered");
        assertNotNull(farmcraftNode.getChild("status"), 
            "/farmcraft status should be registered");
        assertNotNull(farmcraftNode.getChild("help"), 
            "/farmcraft help should be registered");
        assertNotNull(farmcraftNode.getChild("ask"), 
            "/farmcraft ask should be registered");
        assertNotNull(farmcraftNode.getChild("topics"), 
            "/farmcraft topics should be registered");
    }

    @Test
    public void testGuideCommand() throws CommandSyntaxException {
        // Execute /farmcraft guide
        int result = dispatcher.execute("farmcraft guide", mockSource);
        
        // Command should execute successfully (return 1)
        assertEquals(1, result, "/farmcraft guide should execute successfully");
        
        // Verify that messages were sent
        verify(mockSource, atLeastOnce()).sendSuccess(messageCaptor.capture(), eq(false));
        
        // Check that guide content is included
        List<Supplier<Component>> messages = messageCaptor.getAllValues();
        assertTrue(messages.size() > 0, "Guide should send at least one message");
        
        String firstMessage = messages.get(0).get().getString();
        assertTrue(firstMessage.contains("FarmCraft") || firstMessage.contains("Guide"), 
            "Guide message should contain 'FarmCraft' or 'Guide'");
    }

    @Test
    public void testGuideCommandDefaultBehavior() throws CommandSyntaxException {
        // Execute /farmcraft (should default to guide)
        int result = dispatcher.execute("farmcraft", mockSource);
        
        assertEquals(1, result, "/farmcraft should execute successfully (default to guide)");
        verify(mockSource, atLeastOnce()).sendSuccess(any(), eq(false));
    }

    @Test
    public void testStatusCommand() throws CommandSyntaxException {
        // Execute /farmcraft status
        int result = dispatcher.execute("farmcraft status", mockSource);
        
        assertEquals(1, result, "/farmcraft status should execute successfully");
        verify(mockSource, atLeastOnce()).sendSuccess(messageCaptor.capture(), eq(false));
        
        // Verify status message is sent
        List<Supplier<Component>> messages = messageCaptor.getAllValues();
        assertTrue(messages.size() > 0, "Status should send at least one message");
        
        String firstMessage = messages.get(0).get().getString();
        assertTrue(firstMessage.contains("Status") || firstMessage.contains("FarmCraft"), 
            "Status message should contain status information");
    }

    @Test
    public void testHelpCommand() throws CommandSyntaxException {
        // Execute /farmcraft help
        int result = dispatcher.execute("farmcraft help", mockSource);
        
        assertEquals(1, result, "/farmcraft help should execute successfully");
        verify(mockSource, atLeastOnce()).sendSuccess(any(), eq(false));
    }

    @Test
    public void testHelpCommandWithTopic() throws CommandSyntaxException {
        // Execute /farmcraft help recipes
        int result = dispatcher.execute("farmcraft help recipes", mockSource);
        
        assertEquals(1, result, "/farmcraft help recipes should execute successfully");
        verify(mockSource, atLeastOnce()).sendSuccess(messageCaptor.capture(), eq(false));
        
        // Verify that the topic is being processed
        List<Supplier<Component>> messages = messageCaptor.getAllValues();
        assertTrue(messages.size() > 0, "Help command should send messages");
    }

    @Test
    public void testAskCommand() throws CommandSyntaxException {
        // Execute /farmcraft ask how do I craft something?
        int result = dispatcher.execute("farmcraft ask how do I craft something?", mockSource);
        
        assertEquals(1, result, "/farmcraft ask should execute successfully");
        verify(mockSource, atLeastOnce()).sendSuccess(messageCaptor.capture(), eq(false));
        
        // Verify question is being processed
        List<Supplier<Component>> messages = messageCaptor.getAllValues();
        assertTrue(messages.size() > 0, "Ask command should send at least one message");
        
        // Check that the question is acknowledged
        String firstMessage = messages.get(0).get().getString();
        assertTrue(firstMessage.contains("how do I craft something") || 
                   firstMessage.contains("Asking") ||
                   firstMessage.contains("AI"), 
            "Ask command should acknowledge the question");
    }

    @Test
    public void testAskCommandGreedyString() throws CommandSyntaxException {
        // Test that ask command handles multiple spaces and special characters
        int result = dispatcher.execute("farmcraft ask What are the best fertilizers for wheat?", mockSource);
        
        assertEquals(1, result, "/farmcraft ask with complex question should execute successfully");
        verify(mockSource, atLeastOnce()).sendSuccess(any(), eq(false));
    }

    @Test
    public void testTopicsCommand() throws CommandSyntaxException {
        // Execute /farmcraft topics
        int result = dispatcher.execute("farmcraft topics", mockSource);
        
        assertEquals(1, result, "/farmcraft topics should execute successfully");
        verify(mockSource, atLeastOnce()).sendSuccess(messageCaptor.capture(), eq(false));
        
        // Verify topics are being listed
        List<Supplier<Component>> messages = messageCaptor.getAllValues();
        assertTrue(messages.size() > 0, "Topics command should send at least one message");
    }

    @Test
    public void testInvalidSubcommand() {
        // Execute /farmcraft invalidcommand (should fail)
        try {
            dispatcher.execute("farmcraft invalidcommand", mockSource);
            fail("Invalid subcommand should throw CommandSyntaxException");
        } catch (CommandSyntaxException e) {
            // Expected behavior - invalid commands should throw exception
            assertTrue(true);
        }
    }

    @Test
    public void testHelpCommandRequiresArgument() throws CommandSyntaxException {
        // /farmcraft help without argument should default to "overview"
        int result = dispatcher.execute("farmcraft help", mockSource);
        
        assertEquals(1, result, "/farmcraft help without argument should execute with default");
        verify(mockSource, atLeastOnce()).sendSuccess(any(), eq(false));
    }

    @Test
    public void testAskCommandRequiresQuestion() {
        // /farmcraft ask without question should fail
        try {
            dispatcher.execute("farmcraft ask", mockSource);
            fail("Ask command without question should throw CommandSyntaxException");
        } catch (CommandSyntaxException e) {
            // Expected - ask requires a question argument
            assertTrue(true);
        }
    }

    @Test
    public void testAllCommandsHaveProperPermissions() {
        // Test that commands execute with proper permissions
        when(mockSource.hasPermission(anyInt())).thenReturn(true);
        
        try {
            dispatcher.execute("farmcraft guide", mockSource);
            dispatcher.execute("farmcraft status", mockSource);
            dispatcher.execute("farmcraft help overview", mockSource);
            dispatcher.execute("farmcraft topics", mockSource);
            // All should execute without throwing permissions errors
            assertTrue(true, "All commands should execute with proper permissions");
        } catch (CommandSyntaxException e) {
            fail("Commands should not fail with proper permissions: " + e.getMessage());
        }
    }

    @Test
    public void testCommandReturnValues() throws CommandSyntaxException {
        // All successful commands should return 1
        assertEquals(1, dispatcher.execute("farmcraft guide", mockSource));
        assertEquals(1, dispatcher.execute("farmcraft status", mockSource));
        assertEquals(1, dispatcher.execute("farmcraft help test", mockSource));
        assertEquals(1, dispatcher.execute("farmcraft ask test?", mockSource));
        assertEquals(1, dispatcher.execute("farmcraft topics", mockSource));
    }

    @Test
    public void testCommandsExistInDispatcher() {
        // Verify all expected commands are present in the dispatcher tree
        var allCommands = dispatcher.getAllUsage(
            dispatcher.getRoot(), 
            mockSource, 
            true
        );
        
        // Convert to string for easier checking
        String commandsString = String.join("\n", allCommands);
        
        assertTrue(commandsString.contains("farmcraft"), 
            "Dispatcher should contain farmcraft command");
    }
}
