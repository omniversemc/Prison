/*
 * Copyright (C) 2017-2020 The MC-Prison Team
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package tech.mcprison.prison.ranks.commands;

import java.text.DecimalFormat;
import java.util.UUID;

import tech.mcprison.prison.Prison;
import tech.mcprison.prison.commands.Arg;
import tech.mcprison.prison.commands.BaseCommands;
import tech.mcprison.prison.commands.Command;
import tech.mcprison.prison.internal.CommandSender;
import tech.mcprison.prison.internal.Player;
import tech.mcprison.prison.internal.platform.Platform;
import tech.mcprison.prison.localization.Localizable;
import tech.mcprison.prison.output.Output;
import tech.mcprison.prison.ranks.PrisonRanks;
import tech.mcprison.prison.ranks.RankUtil;
import tech.mcprison.prison.ranks.RankUtil.PromoteForceCharge;
import tech.mcprison.prison.ranks.RankUtil.RankupModes;
import tech.mcprison.prison.ranks.RankUtil.RankupStatus;
import tech.mcprison.prison.ranks.RankupResults;
import tech.mcprison.prison.ranks.data.Rank;
import tech.mcprison.prison.ranks.data.RankLadder;
import tech.mcprison.prison.ranks.data.RankPlayer;
import tech.mcprison.prison.ranks.managers.LadderManager;
import tech.mcprison.prison.ranks.managers.PlayerManager;

/**
 * The commands for this module.
 *
 * @author Faizaan A. Datoo
 * @author GABRYCA
 * @author RoyalBlueRanger
 */
public class RankUpCommand 
				extends BaseCommands {

	public RankUpCommand() {
		super( "RankUpCommand" );
	}
	
    /*
     * /rankup command
     */
	
    @Command(identifier = "rankupMax", 
    			description = "Ranks up to the max rank that the player can afford. If the player has the " +
    					"perm ranks.rankupmax.prestige it will try to rankup prestige once it maxes out " +
    					"on the default ladder.", 
    			altPermissions = {"ranks.rankupmax.[ladderName]", "ranks.rankupmax.prestige"},
    			onlyPlayers = false) 
    public void rankUpMax(CommandSender sender,
    		@Arg(name = "ladder", description = "The ladder to rank up on.", def = "default")  String ladder 
    		) {

    	// Not supposed to check perms here... But it is a simple check, and it if works...
    	if ( sender.hasPermission("ranks.rankupmax." + ladder) || sender.hasPermission("ranks.rankupmax.prestiges")) {
			rankUpPrivate(sender, ladder, RankupModes.MAX_RANKS, "ranks.rankupmax.");
		}
    	else {
        	PrisonRanks.getInstance().getRanksMessages()
	    		.getLocalizable( "ranks_rankup__no_permission" )
	    		.withReplacements( "ranks.rankupmax." + ladder.toLowerCase() )
	    		.sendTo( sender );
    	}
    }
	
    @Command(identifier = "rankup", description = "Ranks up to the next rank.", 
			permissions = "ranks.user", altPermissions = "ranks.rankup.[ladderName]", onlyPlayers = false) 
    public void rankUp(CommandSender sender,
		@Arg(name = "ladder", description = "The ladder to rank up on.", def = "default")  String ladder
		) {
        
        if ( !sender.isPlayer() ) {
        	Localizable localManagerLog = PrisonRanks.getInstance().getRanksMessages()
        			.getLocalizable( "ranks_rankup__cannot_run_from_console" );           	
        	Output.get().logInfo( localManagerLog.localize() );
        	return;
        }
        
    	rankUpPrivate(sender, ladder, RankupModes.ONE_RANK, "ranks.rankup." );
    }

    private void rankUpPrivate(CommandSender sender, String ladder, RankupModes mode, String permission ) {

        // RETRIEVE THE LADDER

        // This player has to have permission to rank up on this ladder.
        if (!(ladder.equalsIgnoreCase("prestiges") && 
        		(Prison.get().getPlatform().getConfigBooleanFalse( "prestiges" ) || 
        				Prison.get().getPlatform().getConfigBooleanFalse( "prestige.enabled" ))) && 
        	!ladder.equalsIgnoreCase("default") && 
        	!sender.hasPermission(permission + ladder.toLowerCase())) {
        	
        	PrisonRanks.getInstance().getRanksMessages()
	    		.getLocalizable( "ranks_rankup__no_permission" )
	    		.withReplacements( permission + ladder.toLowerCase() )
	    		.sendTo( sender );
            return;
        }

        
        // 
        if ( mode == null ) {
        	
        	Localizable localManagerLog = PrisonRanks.getInstance().getRanksMessages()
        			.getLocalizable( "ranks_rankup__internal_failure" );           	
        	Output.get().logInfo( localManagerLog.localize() );
        	return;
        }
        
        // Player will always be the player since they have to be online and must be a player:
        Player player = getPlayer( sender, null );
        
        if ( !sender.isPlayer() ) {
        	
        	Localizable localManagerLog = PrisonRanks.getInstance().getRanksMessages()
        			.getLocalizable( "ranks_rankup__cannot_run_from_console" );           	
        	Output.get().logInfo( localManagerLog.localize() );
        	return;
        }

        
        //UUID playerUuid = player.getUUID();
        
		ladder = confirmLadder( sender, ladder );
		if ( ladder == null ) {
			// ladder cannot be null, 
			return;
		}

        RankPlayer rankPlayer = getRankPlayer( sender, player.getUUID(), player.getName() );
        Rank pRank = rankPlayer.getRank( ladder );
        // gets the rank on the default ladder. Used if ladder is not default.
		Rank pRankSecond = rankPlayer.getRank("default"); 
		Rank pRankAfter = null;
		LadderManager lm = PrisonRanks.getInstance().getLadderManager();
		boolean canPrestige = false;

		// If the player is trying to prestige, then the following must be ran to setup the prestige checks:
		if (ladder.equalsIgnoreCase("prestiges")) {

			RankLadder rankLadder = lm.getLadder("default");
			
			if ( rankLadder == null ){
				
				PrisonRanks.getInstance().getRanksMessages()
	    			.getLocalizable( "ranks_rankup__error_no_default_ladder" )
	    			.sendTo( sender );
				return;
			}
			if (!rankLadder.getLowestRank().isPresent()){
				PrisonRanks.getInstance().getRanksMessages()
					.getLocalizable( "ranks_rankup__error_no_lower_rank" )
					.sendTo( sender );
				return;
			}

			Rank rank = rankLadder.getLowestRank().get();

			while (rank.getRankNext() != null) {
				rank = rank.getRankNext();
			}

			if (!(rank == pRankSecond)) {
				PrisonRanks.getInstance().getRanksMessages()
					.getLocalizable( "ranks_rankup__not_at_last_rank" )
					.sendTo( sender );
				return;
			}
			
			// IF everything's ready, this will be true if and only if pRank is not null,
			// and the prestige method will start
			canPrestige = true;
		}
        
        // Get currency if it exists, otherwise it will be null if the Rank has no currency:
        String currency = rankPlayer == null || pRank == null ? null : pRank.getCurrency();

		boolean rankupWithSuccess = false;

        if (rankPlayer != null ) {
        	
        	// Performs the actual rankup here:
        	RankupResults results = new RankUtil().rankupPlayer(player, rankPlayer, ladder, sender.getName());
        	
        	processResults( sender, player.getName(), results, true, null, ladder, currency );

        	// If the last rankup attempt was successful and they are trying to rankup as many times as possible: 
        	if (results.getStatus() == RankupStatus.RANKUP_SUCCESS && mode == RankupModes.MAX_RANKS && 
        									!ladder.equals("prestiges")) {
        		rankUpPrivate( sender, ladder, mode, permission );
        	}
        	if (results.getStatus() == RankupStatus.RANKUP_SUCCESS){
        		rankupWithSuccess = true;
			}

        	// Get the player rank after
        	pRankAfter = rankPlayer.getRank(ladder);

        	
        	// Prestige method if canPrestige and a successful rankup. pRank cannot be the same as pRankAfter:
        	if ( canPrestige && rankupWithSuccess && pRankAfter != null && pRank != pRankAfter ) {
        		prestigePlayer( sender, player, rankPlayer, pRankAfter, lm );
        	}
        	else if ( canPrestige ) {
        		PrisonRanks.getInstance().getRanksMessages()
					.getLocalizable( "ranks_rankup__not_able_to_prestige" )
					.sendTo( sender );
        	}
        	
        }
	}

    /**
     * <p>Perform the final prestige actions if prestige is requested (canPrestige) and if the 
     * rankup was successful.  It also assumes that pRankAfter is not null and not the same
     * as pRank, which would indicate something went wrong with the rankup.
     * </p>
     * 
     * <p>This function will reset the player's default ladder, if the configuration setting
     * 'prestige.resetDefaultLadder' has been enabled.  Otherwise the default ladder is not 
     * modified.
     * </p>
     * 
     * <p>This function will also reset the player's balance if the configuration 
     * 'prestige.resetMoney' is enabled.
     * </p>
     * 
     * 
     * @param sender
     * @param player
     * @param rankPlayer
     * @param pRankAfter
     * @param lm
     */
	private void prestigePlayer(CommandSender sender, Player player, RankPlayer rankPlayer, 
						Rank pRankAfter, LadderManager lm ) {
		
		Platform platform = Prison.get().getPlatform();
		boolean resetBalance = platform.getConfigBooleanTrue( "prestige.resetMoney" );
		boolean resetDefaultLadder = platform.getConfigBooleanTrue( "prestige.resetDefaultLadder" );
		
		boolean success = true;
		
		if ( resetDefaultLadder ) {
			
			// Get the player rank after, just to check if it has success Conditions
//			if (willPrestige && rankupWithSuccess && pRankAfter != null && pRank != pRankAfter) {
				// Set the player rank to the first one of the default ladder
				
				// Call the function directly and skip using dispatch commands:
				setRank( sender, player.getName(), lm.getLadder("default").getLowestRank().get().getName(), "default" );
				
//				PrisonAPI.dispatchCommand("ranks set rank " + player.getName() + " " + 
//						lm.getLadder("default").getLowestRank().get().getName() + " default");
				// Get that rank
				Rank pRankSecond = rankPlayer.getRank("default");
				// Check if the ranks match

				if (pRankSecond != lm.getLadder("default").getLowestRank().get()) {
					
					PrisonRanks.getInstance().getRanksMessages()
						.getLocalizable( "ranks_rankup__not_able_to_reset_rank" )
						.sendTo( sender );
					success = false;
				}
//			}
		}
		
		if ( success && resetBalance ) {
			
			// set the player's balance to zero:
			rankPlayer.setBalance( 0 );
			PrisonRanks.getInstance().getRanksMessages()
				.getLocalizable( "ranks_rankup__balance_set_to_zero" )
				.sendTo( sender );
		}
		
		if ( success ) {
			// Send a message to the player because he did prestige!
			PrisonRanks.getInstance().getRanksMessages()
				.getLocalizable( "ranks_rankup__prestige_successful" )
				.withReplacements(
						pRankAfter.getTag()
	    				)
				.sendTo( sender );
		}
		else {
			
			PrisonRanks.getInstance().getRanksMessages()
				.getLocalizable( "ranks_rankup__prestige_failure" )
				.withReplacements(
						pRankAfter.getTag()
						)
				.sendTo( sender );
		}

	}


	@Command(identifier = "ranks promote", description = "Promotes a player to the next rank.",
    			permissions = "ranks.promote", onlyPlayers = false) 
    public void promotePlayer(CommandSender sender,
    	@Arg(name = "playerName", def = "", description = "Player name") String playerName,
        @Arg(name = "ladder", description = "The ladder to promote on.", def = "default") String ladder,
        @Arg(name = "chargePlayers", description = "Force the player to pay for the rankup (no_charge, charge_player)", 
        					def = "no_charge") String chargePlayer
    		) {

    	Player player = getPlayer( sender, playerName );
    	
    	if (player == null) {
    		PrisonRanks.getInstance().getRanksMessages()
	    		.getLocalizable( "ranks_rankup__rankup_failure_must_be_online_player" )
	    		.sendTo( sender );
    		
//    		sender.sendMessage( "&3You must be a player in the game to run this command, " +
//    															"and/or the player must be online." );
    		return;
    	}
    	
    	PromoteForceCharge pForceCharge = PromoteForceCharge.fromString( chargePlayer );
    	if ( pForceCharge == null|| pForceCharge == PromoteForceCharge.refund_player ) {
    		
			PrisonRanks.getInstance().getRanksMessages()
				.getLocalizable( "ranks_rankup__invalid_charge_value" )
				.withReplacements(
						PromoteForceCharge.no_charge.name(), 
						PromoteForceCharge.charge_player.name()
						)
				.sendTo( sender );
    		return;
    	}

        UUID playerUuid = player.getUUID();
        
		ladder = confirmLadder( sender, ladder );

        RankPlayer rankPlayer = getRankPlayer( sender, playerUuid, player.getName() );
        Rank pRank = rankPlayer.getRank( ladder );
        
        // Get currency if it exists, otherwise it will be null if the Rank has no currency:
        String currency = rankPlayer == null || pRank == null ? null : pRank.getCurrency();
        
        

        if ( ladder != null && rankPlayer != null ) {
        	RankupResults results = new RankUtil().promotePlayer(player, rankPlayer, ladder, 
        												player.getName(), sender.getName(), pForceCharge);
        	
        	processResults( sender, player.getName(), results, true, null, ladder, currency );
        }
    }


    @Command(identifier = "ranks demote", description = "Demotes a player to the next lower rank.", 
    			permissions = "ranks.demote", onlyPlayers = false) 
    public void demotePlayer(CommandSender sender,
    	@Arg(name = "playerName", def = "", description = "Player name") String playerName,
        @Arg(name = "ladder", description = "The ladder to demote on.", def = "default") String ladder,
        @Arg(name = "chargePlayers", description = "Refund the player for the demotion (no_charge, refund_player)", 
        				def = "no_charge") String refundPlayer
        ) {

    	Player player = getPlayer( sender, playerName );
    	
    	if (player == null) {
    		PrisonRanks.getInstance().getRanksMessages()
	    		.getLocalizable( "ranks_rankup__rankup_failure_must_be_online_player" )
	    		.sendTo( sender );
    		
//    		sender.sendMessage( "&3You must be a player in the game to run this command, " +
//    															"and/or the player must be online." );
    		return;
    	}
    	
    	PromoteForceCharge pForceCharge = PromoteForceCharge.fromString( refundPlayer );
    	if ( pForceCharge == null || pForceCharge == PromoteForceCharge.charge_player ) {
			PrisonRanks.getInstance().getRanksMessages()
				.getLocalizable( "ranks_rankup__invalid_refund_value" )
				.withReplacements(
						PromoteForceCharge.no_charge.name(), 
						PromoteForceCharge.refund_player.name()
						)
				.sendTo( sender );
    		return;
    	}
    	
        UUID playerUuid = player.getUUID();
        
		ladder = confirmLadder( sender, ladder );

        RankPlayer rankPlayer = getRankPlayer( sender, playerUuid, player.getName() );
        Rank pRank = rankPlayer.getRank( ladder );
        
        // Get currency if it exists, otherwise it will be null if the Rank has no currency:
        String currency = rankPlayer == null || pRank == null ? null : pRank.getCurrency();

        if ( ladder != null && rankPlayer != null ) {
        	RankupResults results = new RankUtil().demotePlayer(player, rankPlayer, ladder, 
        												player.getName(), sender.getName(), pForceCharge);
        	
        	processResults( sender, player.getName(), results, false, null, ladder, currency );
        }
    }


    @Command(identifier = "ranks set rank", description = "Sets a player to a specified rank on a ladder, " +
    		"or remove a player from a ladder (delete player rank).  Or if you use '*all*' for player name, " +
    		"then it will run this command on all players registered with Prison. When *all* is combined with " +
    		"the rankName '*same*' it will reapply the same rank to each player which will rerun the rank " +
    		"commands for the players.  If rank 'A' is your starting rank, you can use '*all*' and 'A' to " +
    		"reset all players to the starting rank; next you will need to -remove- all prestige ladders " +
    		"from all players too.", 
    			permissions = "ranks.setrank", onlyPlayers = false) 
    public void setRank(CommandSender sender,
    	@Arg(name = "playerName", def = "", description = "Player name, or [*all*]") String playerName,
    	@Arg(name = "rankName", description = "The rank to assign to the player, or [-remove-, *same*] " +
    						"to deleete the player from the rank.") String rank,
        @Arg(name = "ladder", description = "The ladder to demote on.", def = "default") String ladder) {

    	if ( "*all*".equalsIgnoreCase( playerName )) {
    		PlayerManager pm = PrisonRanks.getInstance().getPlayerManager();
    		
    		for ( RankPlayer player : pm.getPlayers() ) {
    			
    			Player targetPlayer = getPlayer( null, player.getName() );
    			if ( targetPlayer != null ) {
    				
    				String targetRank = rank.equalsIgnoreCase("*same*") ? player.getRank( ladder ).getName() : rank;
    				setPlayerRank( targetPlayer, targetRank, ladder, sender );
    			}
    		}
    		
    	}
    	else {
    		
    		Player player = getPlayer( sender, playerName );
    		
    		if (player == null) {
    			PrisonRanks.getInstance().getRanksMessages()
		    		.getLocalizable( "ranks_rankup__rankup_failure_must_be_online_player" )
		    		.sendTo( sender );
    			
//    			sender.sendMessage( "&3You must be a player in the game to run this command, " +
//    					"and/or the player must be online." );
    			return;
    		}
    		
    		setPlayerRank( player, rank, ladder, sender );
    	}
    }

    
    @Command(identifier = "ranks remove rank", description = "Removes a player from a specified ladder " +
    		"(delete player rank). This is an alias for /ranks set rank <playerName> -remove- <ladder>.", 
    		permissions = "ranks.setrank", onlyPlayers = false) 
    public void removeRank(CommandSender sender,
    		@Arg(name = "playerName", def = "", description = "Player name") String playerName,
    		@Arg(name = "ladder", description = "The ladder to demote on.", def = "default") String ladder) {
    	
    	setRank( sender, playerName, "-remove-", ladder );
    	
    }
    
    
	private void setPlayerRank( Player player, String rank, String ladder, CommandSender sender ) {
		UUID playerUuid = player.getUUID();
        
		ladder = confirmLadder( sender, ladder );

        RankPlayer rankPlayer = getRankPlayer( sender, playerUuid, player.getName() );
        Rank pRank = rankPlayer.getRank( ladder );
        
        // Get currency if it exists, otherwise it will be null if the Rank has no currency:
        String currency = rankPlayer == null || pRank == null ? null : pRank.getCurrency();

        if ( ladder != null && rankPlayer != null ) {
        	RankupResults results = new RankUtil().setRank(player, rankPlayer, ladder, rank, 
        												player.getName(), sender.getName());
        	
        	processResults( sender, player.getName(), results, true, rank, ladder, currency );
        }
	}



	public String confirmLadder( CommandSender sender, String ladderName ) {
		String results = null;
		RankLadder ladder = PrisonRanks.getInstance().getLadderManager().getLadder(ladderName);

        // The ladder doesn't exist
        if ( ladder == null ) {
        	PrisonRanks.getInstance().getRanksMessages()
        		.getLocalizable( "ranks_rankup__rankup_failure_invalid_ladder" )
        		.withReplacements( ladderName )
        		.sendTo( sender );
        	
//            Output.get().sendError(sender, "The ladder '%s' does not exist.", ladderName);
        }
        else {
        	results = ladder.getName();
        }
        return results;
	}


	public RankPlayer getRankPlayer( CommandSender sender, UUID playerUuid, String playerName ) {
		
		RankPlayer player =
							PrisonRanks.getInstance().getPlayerManager().getPlayer(playerUuid, playerName);

        // Well, this isn't supposed to happen...
        if ( player == null ) {
        	PrisonRanks.getInstance().getRanksMessages()
	    		.getLocalizable( "ranks_rankup__rankup_failure_to_get_rankplayer" )
	    		.sendTo( sender );
        	
//            Output.get().sendError(sender,
//                "You don't exist! The server has no records of you. Try rejoining, " +
//            									"or contact a server administrator for help.");
        }

        return player;
	}


	public void processResults( CommandSender sender, String playerName, 
					RankupResults results, 
					boolean rankup, String rank, String ladder, String currency ) {
	
		switch (results.getStatus()) {
            case RANKUP_SUCCESS:
            	
            	String messageId = rankup ? "ranks_rankup__rankup_success" : "ranks_rankup__demote_success";

            	String messagNoPlayerName = PrisonRanks.getInstance().getRanksMessages()
            			.getLocalizable( "ranks_rankup__rankup_no_player_name" ).localize();
            	
            	Localizable localManager = PrisonRanks.getInstance().getRanksMessages()
            			.getLocalizable( messageId )
            			.withReplacements(
            					
            					(playerName == null ? messagNoPlayerName : playerName),
            					(results.getTargetRank() == null ? "" : results.getTargetRank().getName()), 
            					(results.getMessage() != null ? results.getMessage() : "")
            				);
            	
            	Localizable localManagerLog = PrisonRanks.getInstance().getRanksMessages()
            			.getLocalizable( "ranks_rankup__log_rank_change" )
            			.withReplacements(
            					
            					sender.getName(), localManager.localize()
            				);           	
            	Output.get().logInfo( localManagerLog.localize() );
            	
            	if ( Prison.get().getPlatform().getConfigBooleanFalse( "broadcast-rankups" ) ) {
            		String messagNoPlayerNameBroadcast = PrisonRanks.getInstance().getRanksMessages()
            				.getLocalizable( "ranks_rankup__rankup_no_player_name_broadcast" ).localize();
            		
            		PrisonRanks.getInstance().getRanksMessages()
	            		.getLocalizable( messageId )
	            		.withReplacements(
            				
            				(playerName == null ? messagNoPlayerNameBroadcast : playerName),
            				(results.getTargetRank() == null ? "" : results.getTargetRank().getName()), 
            				(results.getMessage() != null ? results.getMessage() : "")
            			)
	            		.broadcast();
            	}
            	else {
            		localManager.sendTo( sender );
            	}
//            	if ( rankup ) {
//            		String message = String.format( "Congratulations! %s ranked up to rank '%s'. %s",
//            				(playerName == null ? "You have" : playerName),
//            				(results.getTargetRank() == null ? "" : results.getTargetRank().getName()), 
//            				(results.getMessage() != null ? results.getMessage() : "") );
//            		Output.get().sendInfo(sender, message);
//            		
//            		if ( Prison.get().getPlatform().getConfigBooleanFalse( "broadcast-rankups" ) ) {
//            			
//            			String messageGlobal = String.format( "Congratulations! %s ranked up to rank '%s'.",
//            					(playerName == null ? "Someone" : playerName),
//            					(results.getTargetRank() == null ? "" : results.getTargetRank().getName()) );
//            			broadcastToWholeServer( sender, messageGlobal );
//            		}
//            	} else {
            		
//	            	String message = String.format( "Unfortunately, %s has been demoted to rank '%s'. %s",
//            				(playerName == null ? "You have" : playerName),
//            				(results.getTargetRank() == null ? "" : results.getTargetRank().getName()), 
//            				(results.getMessage() != null ? results.getMessage() : ""));
//            		Output.get().sendInfo(sender, message);
//            		Output.get().logInfo( "%s initiated rank change: %s", sender.getName(), message );
//            		
//            		if ( Prison.get().getPlatform().getConfigBooleanFalse( "broadcast-rankups" ) ) {
//            			
//            			String messageGlobal = String.format( "Unfortunately, %s has been demoted to rank '%s'.",
//            					(playerName == null ? "Someone" : playerName),
//            					(results.getTargetRank() == null ? "" : results.getTargetRank().getName()) );
//            			 broadcastToWholeServer( sender, messageGlobal );
//            		}
//				}
                break;
            case RANKUP_CANT_AFFORD:
            	DecimalFormat dFmt = new DecimalFormat("#,##0.00");
            	
            	PrisonRanks.getInstance().getRanksMessages()
	        		.getLocalizable( "ranks_rankup__rankup_cant_afford" )
	        		.withReplacements(
	    				
	        				dFmt.format( results.getTargetRank() == null ? 0 : results.getTargetRank().getCost()), 
	                        results.getTargetRank().getCurrency() == null ? "" : results.getTargetRank().getCurrency()
	    			)
	        		.sendTo( sender );
	            	
//                Output.get().sendError(sender,
//                    "You don't have enough money to rank up! The next rank costs %s %s.",
//                    dFmt.format( results.getTargetRank() == null ? 0 : results.getTargetRank().getCost()), 
//                    results.getTargetRank().getCurrency() == null ? "" : results.getTargetRank().getCurrency() );
//                break;
            case RANKUP_LOWEST:
            	{
            		String messagYouAre = PrisonRanks.getInstance().getRanksMessages()
            				.getLocalizable( "ranks_rankup__rankup_you_are" ).localize();
            		
            		PrisonRanks.getInstance().getRanksMessages()
            		.getLocalizable( "ranks_rankup__rankup_lowest" )
            		.withReplacements(
            				
            				(playerName == null ? messagYouAre : playerName)
            				)
            		.sendTo( sender );
            	}
            	
//            	Output.get().sendInfo(sender, "%s already at the lowest rank!",
//            				(playerName == null ? "You are" : playerName));
            	break;
            case RANKUP_HIGHEST:
            	{
            		String messagYouAre = PrisonRanks.getInstance().getRanksMessages()
            				.getLocalizable( "ranks_rankup__rankup_you_are" ).localize();
            		
            		PrisonRanks.getInstance().getRanksMessages()
            		.getLocalizable( "ranks_rankup__rankup_highest" )
            		.withReplacements(
            				
            				(playerName == null ? messagYouAre : playerName)
            				)
            		.sendTo( sender );
            	}
            	
//                Output.get().sendInfo(sender, "%s already at the highest rank!",
//            				(playerName == null ? "You are" : playerName));
                break;
            case RANKUP_FAILURE:
            	PrisonRanks.getInstance().getRanksMessages()
	        		.getLocalizable( "ranks_rankup__rankup_failure" )
	        		.sendTo( sender );
//                Output.get().sendError(sender,
//                    "Generic rankup failure. Review rankup details to identify why.");
                break;
            case RANKUP_FAILURE_COULD_NOT_LOAD_PLAYER:
            	PrisonRanks.getInstance().getRanksMessages()
	            	.getLocalizable( "ranks_rankup__rankup_failed_to_load_player" )
	            	.sendTo( sender );
            	
//            	Output.get().sendError(sender,
//            			"Failed to load player.");
            	break;
            case RANKUP_FAILURE_COULD_NOT_LOAD_LADDER:
            	PrisonRanks.getInstance().getRanksMessages()
            		.getLocalizable( "ranks_rankup__rankup_failed_to_load_ladder" )
            		.sendTo( sender );
            	
//            	Output.get().sendError(sender,
//            			"Failed to load ladder.");
            	break;
            case RANKUP_FAILURE_UNABLE_TO_ASSIGN_RANK:
            	PrisonRanks.getInstance().getRanksMessages()
        			.getLocalizable( "ranks_rankup__rankup_failed_to_assign_rank" )
        			.sendTo( sender );
            	
//            	Output.get().sendError(sender,
//            			"Failed to assign a rank.  Review rankup details to identify why.");
            	break;
            case RANKUP_FAILURE_COULD_NOT_SAVE_PLAYER_FILE:
            	PrisonRanks.getInstance().getRanksMessages()
        			.getLocalizable( "ranks_rankup__rankup_failed_to_save_player_file" )
        			.sendTo( sender );
            	
//            	Output.get().sendError(sender,
//            			"Failed to retrieve or write data. Your files may be corrupted. " +
//            			"Alert a server administrator.");
            	break;
            case RANKUP_NO_RANKS:
            	PrisonRanks.getInstance().getRanksMessages()
        			.getLocalizable( "ranks_rankup__rankup_no_ranks" )
        			.sendTo( sender );            	
            	
//                Output.get().sendError(sender, "There are no ranks in this ladder.");
                break;
            case RANKUP_FAILURE_RANK_DOES_NOT_EXIST:
            	PrisonRanks.getInstance().getRanksMessages()
	    			.getLocalizable( "ranks_rankup__rankup_rank_does_not_exist" )
	    			.withReplacements( rank )
	    			.sendTo( sender ); 
            	
//            	Output.get().sendError(sender, "The rank %s does not exist on this server.", rank);
            	break;
			case RANKUP_FAILURE_RANK_IS_NOT_IN_LADDER:
				PrisonRanks.getInstance().getRanksMessages()
	    			.getLocalizable( "ranks_rankup__rankup_rank_is_not_in_ladder" )
	    			.withReplacements( rank, ladder )
	    			.sendTo( sender );
				
//				Output.get().sendError(sender, "The rank %s does not exist in the ladder %s.", rank, ladder);
				break;
            
			case RANKUP_FAILURE_CURRENCY_IS_NOT_SUPPORTED:
				PrisonRanks.getInstance().getRanksMessages()
	    			.getLocalizable( "ranks_rankup__rankup_currency_is_not_supported" )
	    			.withReplacements( results.getTargetRank().getCurrency() )
	    			.sendTo( sender );	
				
//				Output.get().sendError(sender, "The currency, %s, is not supported by any " +
//													"loaded economies.", results.getTargetRank().getCurrency());
				break;
				
			case RANKUP_LADDER_REMOVED:
				PrisonRanks.getInstance().getRanksMessages()
	    			.getLocalizable( "ranks_rankup__rankup_ladder_removed" )
	    			.withReplacements( ladder )
	    			.sendTo( sender );
				
//				Output.get().send(sender, "The ladder %s was removed.", ladder);
				break;
				
			case RANKUP_FAILURE_REMOVING_LADDER:
				PrisonRanks.getInstance().getRanksMessages()
					.getLocalizable( "ranks_rankup__rankup_failure_removing_ladder" )
					.withReplacements( ladder )
					.sendTo( sender );
				
//				Output.get().sendError(sender, "The ladder %s could not be removed.", ladder);
				break;
				
			case IN_PROGRESS:
				PrisonRanks.getInstance().getRanksMessages()
					.getLocalizable( "ranks_rankup__rankup_in_progress_failure" )
					.sendTo( sender );
				
//				Output.get().sendError(sender, "Rankup failed to complete normally. No status was set.");
				break;
			default:
				break;
        }
	}

	
	
//	private void broadcastToWholeServer( CommandSender sender, String message ) {
//    	
//		String broadcastRankups = Prison.get().getPlatform().getConfigString( "broadcast-rankups" );
//		
//		if ( broadcastRankups == null || broadcastRankups.equalsIgnoreCase( "true" ) ) {
//			
//			Player player = getPlayer( sender, sender.getName() );
//			List<Player> players = Prison.get().getPlatform().getOnlinePlayers();
//			
//			for ( Player p : players ) {
//				if ( !p.equals( player ) ) {
//					p.sendMessage( message );
//				}
//			}
//		}
//    }
    
}
