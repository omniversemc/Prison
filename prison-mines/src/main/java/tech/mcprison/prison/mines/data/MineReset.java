package tech.mcprison.prison.mines.data;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.TreeMap;

import tech.mcprison.prison.Prison;
import tech.mcprison.prison.internal.Player;
import tech.mcprison.prison.internal.World;
import tech.mcprison.prison.internal.block.Block;
import tech.mcprison.prison.internal.block.PrisonBlock;
import tech.mcprison.prison.internal.block.PrisonBlockStatusData;
import tech.mcprison.prison.mines.PrisonMines;
import tech.mcprison.prison.mines.data.MineScheduler.MineJob;
import tech.mcprison.prison.mines.data.MineScheduler.MineResetActions;
import tech.mcprison.prison.mines.events.MineResetEvent;
import tech.mcprison.prison.mines.features.MineLinerBuilder;
import tech.mcprison.prison.mines.features.MineLinerBuilder.LinerPatterns;
import tech.mcprison.prison.mines.features.MineMover;
import tech.mcprison.prison.mines.features.MineTargetBlockKey;
import tech.mcprison.prison.mines.features.MineTargetPrisonBlock;
import tech.mcprison.prison.mines.features.MineTracerBuilder;
import tech.mcprison.prison.mines.tasks.MinePagedResetAsyncTask;
import tech.mcprison.prison.mines.tasks.MinePagedResetAsyncTask.MineResetType;
import tech.mcprison.prison.mines.tasks.MineTeleportTask;
import tech.mcprison.prison.output.Output;
import tech.mcprison.prison.tasks.PrisonCommandTask;
import tech.mcprison.prison.tasks.PrisonRunnable;
import tech.mcprison.prison.tasks.PrisonTaskSubmitter;
import tech.mcprison.prison.util.BlockType;
import tech.mcprison.prison.util.Bounds;
import tech.mcprison.prison.util.Bounds.Edges;
import tech.mcprison.prison.util.Location;

public abstract class MineReset
	extends MineData
{
	/**
	 * <p>Minecraft ticks have 20 per seconds, which is 50 MS per tick. 
	 * The value for MINE_RESET__MAX_PAGE_ELASPSED_TIME_MS is intended
	 * to be used as a threshold to determine when to stop placing blocks
	 * and resubmit in the sync job queue to allow other processes to run.
	 * </p>
	 * 
	 * <p>This value, in milliseconds, is not hard-and-fast guaranteed to be 
	 * caught exactly at that time. It is instead used to check to see if
	 * the current process has exceeded this value, which may be many times
	 * greater than this value.
	 * </p>
	 * 
	 * <p>This value is subject to refinement and tuning to ensure better and
	 * more accurate responses.
	 * </p>
	 */
	public static final long MINE_RESET__MAX_PAGE_ELASPSED_TIME_MS = 75;

	
	/**
	 * <p>This is the time in ticks that is used when submitting 
	 * the reset jobs. A value of 0 could cause the server to lock up since
	 * it does not have enough time to deal with other tasks.  
	 * </p>
	 * 
	 * <p>One tick is 50 milliseconds, 2 is 100 milliseconds or 1/10 of 
	 * a second.
	 * </p>
	 */
	public static final long MINE_RESET__PAGE_SUBMIT_DELAY_TICKS = 1;
	
	/**
	 * <p>When placing blocks, this is the block count that is used to check for
	 * the elapsed time.
	 * </p>
	 * 
	 * <p>It does not matter if some of those blocks were placed in a prior 
	 * page, for it is the elapsed time that is important.  It's also important
	 * that there are not too many checks since it will just contribute to lag.
	 * The value needs to be moderately large.
	 * </p>
	 * 
	 * <p>This value is subject to refinement and tuning to ensure better and
	 * more accurate responses.
	 * </p>
	 */
	public static final long MINE_RESET__PAGE_TIMEOUT_CHECK__BLOCK_COUNT = 250;
	
	
	public static final long MINE_RESET__AIR_COUNT_BASE_DELAY = 30000L; // 30 seconds
	

	private List<MineTargetPrisonBlock> mineTargetPrisonBlocks;
	private TreeMap<MineTargetBlockKey, MineTargetPrisonBlock> mineTargetPrisonBlocksMap;
	
	private MineJob currentJob;
	
	private int resetPage = 0;
	private int resetPosition = 0;
	
	private long resetPageMaxPageElapsedTimeMs = -1;
	private long resetPagePageSubmitDelayTicks = -1;
	private long resetPageTimeoutCheckBlockCount = -1;
	
	private int airCountOriginal = 0;
	private int airCount = 0;
	private long airCountTimestamp = 0L;
	private long airCountElapsedTimeMs = 0L;
	
	
	
	private long statsResetTimeMS = 0;
	private long statsBlockGenTimeMS = 0;
	private long statsBlockUpdateTimeMS = 0;
	
	// Note: The time it takes to teleport players and broadcast is so trivial
	//       that they are being disabled to reduce the clutter and memory load.
//	private long statsTeleport1TimeMS = 0;
//	private long statsTeleport2TimeMS = 0;
//	private long statsMessageBroadcastTimeMS = 0;
	
	private int statsResetPages = 0;
	private long statsResetPageBlocks = 0;
	private long statsResetPageMs = 0;
	
	
	private List<Long> statsMineSweeperTaskMs;
	private boolean mineSweeperSubmitted = false;
	
	public MineReset() {
		super();
		
		this.mineTargetPrisonBlocks = new ArrayList<>();
		this.mineTargetPrisonBlocksMap = new TreeMap<>();
		
		this.statsMineSweeperTaskMs = new ArrayList<>();

		this.currentJob = null;
		
	}

    /**
     * <p>This initialize function gets called after the classes are
     * instantiated, and is initiated from Mine class and propagates
     * to the MineData class.  Good for kicking off the scheduler.
     * </p>
     * 
     * <p>Once the mine has been loaded in to memory, the number of 
     * air blocks must be counted to properly set the blockBreakCount.
     * </p>
     * 
     */
	@Override
	protected void initialize() {
    	super.initialize();
    	
    	if ( !isVirtual() ) {
    		
    		// Once the mine has been loaded, MUST get a count of all air blocks.
    		refreshBlockBreakCountUponStartup();
    	}
    }
    
    /**
     * <p>Optimized the mine reset to focus on itself.  Also set the Y plane to refresh at the top and work its
     * way down.  That way if the play is teleported to the top, it will appear like the whole mine has reset
     * instantly and they will not see the delay from the bottom of the mine working up to the top.  This will
     * also reduce the likelihood of the player falling back in to the mine if there is no spawn set.
     * </p>
     * 
     * <p>The ONLY code that could be asynchronous ran is the random generation of the blocks.  The other  
     * lines of code is using bukkit and/or spigot api calls which MUST be ran synchronously.
     * </p>
     */
    protected void resetSynchonously() {
    	
    	if ( isDeleted() ) {
    		// if the mine is deleted, just return without doing anything. This will
    		// cancel the job.
    		return;
    	}

		long start = System.currentTimeMillis();
		
		// The all-important event
		MineResetEvent event = new MineResetEvent(this);
		Prison.get().getEventBus().post(event);
		if (!event.isCanceled()) {
			resetSynchonouslyInternal();
		}
		
		long stop = System.currentTimeMillis();
		setStatsResetTimeMS( stop - start );
		
		// Tie to the command stats mode so it logs it if stats are enabled:
		if ( PrisonMines.getInstance().getMineManager().isMineStats() ) {
			DecimalFormat dFmt = new DecimalFormat("#,##0");
			Output.get().logInfo("&cMine reset: &7" + getTag() + 
					"&c  Blocks: &7" + dFmt.format( getBounds().getTotalBlockCount() ) + 
					statsMessage() );
		}
    	
    }

    /**
     * <p>This function now follows the general behavior as the async reset in that it now
     * uses a target block listing to help ensure the constraints are honored when 
     * generating the blocks.
     * </p>
     * 
     */
	private void resetSynchonouslyInternal() {
		try {
			
			if ( isVirtual() || isDeleted() ) {
				// Mine is virtual and cannot be reset.  Just skip this with no error messages.
				// If the mine is deleted, just return without doing anything.
				return;
			}
			
			if ( !isEnabled() ) {
				Output.get().logError(
						String.format( "MineReset: Reset failure: Mine is not enabled. " +
								"Ensure world exists. mine= %s ", 
								getName()  ));
				return;
			}
			
			
			teleportAllPlayersOut();
//			setStatsTeleport1TimeMS(
//					teleportAllPlayersOut( getBounds().getyBlockMax() ) );
			
			
			// Reset stats:
			resetStats();

			
			generateBlockListAsync();
			
			
			if ( !getCurrentJob().getResetActions().contains( MineResetActions.NO_COMMANDS )) {
				
				// Before reset commands:
				if ( getResetCommands() != null && getResetCommands().size() > 0 ) {
					
					for (String command : getResetCommands() ) {
// 	        		String formatted = cmd.replace("{player}", prisonPlayer.getName())
// 	        				.replace("{player_uid}", player.uid.toString());
						if ( command.startsWith( "before: " )) {
							String cmd = command.replace( "before: ", "" );
							
     						PrisonCommandTask cmdTask = new PrisonCommandTask( "MineReset: Before:" );
     						cmdTask.submitCommandTask( cmd );

							//PrisonAPI.dispatchCommand(cmd);
						}
					}
				}
			}

			
    		MinePagedResetAsyncTask resetTask = new MinePagedResetAsyncTask( (Mine) this, MineResetType.normal );
    		resetTask.submitTaskAsync();
//			resetAsynchonouslyUpdate( false );
			
		
			
			// If a player falls back in to the mine before it is fully done being reset, 
			// such as could happen if there is lag or a lot going on within the server, 
			// this will TP anyone out who would otherwise suffocate.  I hope! lol
			teleportAllPlayersOut();
//			setStatsTeleport2TimeMS(
//					teleportAllPlayersOut( getBounds().getyBlockMax() ) );
			
			
			incrementResetCount();
			
			if ( !getCurrentJob().getResetActions().contains( MineResetActions.NO_COMMANDS )) {
				
				// After reset commands:
				if ( getResetCommands() != null && getResetCommands().size() > 0 ) {
					
					for (String command : getResetCommands() ) {
// 	        		String formatted = cmd.replace("{player}", prisonPlayer.getName())
// 	        				.replace("{player_uid}", player.uid.toString());
						if ( command.startsWith( "after: " )) {
							String cmd = command.replace( "after: ", "" );
							
     						PrisonCommandTask cmdTask = new PrisonCommandTask( "MineReset: After:" );
     						cmdTask.submitCommandTask( cmd );

							//PrisonAPI.dispatchCommand(cmd);
						}
					}
				}
			}

			
			// Broadcast message to all players within a certain radius of this mine:
			broadcastResetMessageToAllPlayersWithRadius();
//			broadcastResetMessageToAllPlayersWithRadius( MINE_RESET__BROADCAST_RADIUS_BLOCKS );
			
			
			submitTeleportGlassBlockRemoval();
			
			
			// If part of a chained_resets, then kick off the next reset:
			if ( getCurrentJob().getResetActions().contains( MineResetActions.CHAINED_RESETS )) {
				
				PrisonMines pMines = PrisonMines.getInstance();
				pMines.resetAllMinesNext();
			}

			
		} catch (Exception e) {
			Output.get().logError("&cFailed to reset mine " + getName(), e);
		}
	}

    
    public String statsMessage() {
    	StringBuilder sb = new StringBuilder();
    	DecimalFormat dFmt = new DecimalFormat("#,##0.000");
    	DecimalFormat iFmt = new DecimalFormat("#,##0");
    	
    	sb.append( "&3 ResetTime: &7" );
    	sb.append( dFmt.format(getStatsResetTimeMS() / 1000.0d )).append( " s " );
    	
    	sb.append( "&3 BlockGenTime: &7" );
    	sb.append( dFmt.format(getStatsBlockGenTimeMS() / 1000.0d )).append( " s " );
    	
//    	sb.append( "&3 TP1: &7" );
//    	sb.append( dFmt.format(getStatsTeleport1TimeMS() / 1000.0d ));

    	sb.append( "&3 BlockUpdateTime: &7" );
    	sb.append( dFmt.format(getStatsBlockUpdateTimeMS() / 1000.0d )).append( " s " );
    	
//    	sb.append( "&3 TP2: &7" );
//    	sb.append( dFmt.format(getStatsTeleport2TimeMS() / 1000.0d ));
    	
//    	sb.append( "&3 Msg: &7" );
//    	sb.append( dFmt.format(getStatsMessageBroadcastTimeMS() / 1000.0d ));
    	
    	sb.append( "&3 ResetPages: &7" );
    	sb.append( iFmt.format(getStatsResetPages() ));
    	
    	double avgBlocks = getStatsResetPages() == 0 ? 0 : 
    			getStatsResetPageBlocks() / getStatsResetPages();
    	double avgMs = getStatsResetPages() == 0 ? 0 :
    			getStatsResetPageMs() / getStatsResetPages();
    	
    	sb.append( "&3 avgBlocksPerPage: &7" );
    	sb.append( dFmt.format(avgBlocks));
    	
    	sb.append( "&3 avgMsPerPage: &7" );
    	sb.append( dFmt.format(avgMs));
    	
    	sb.append( statsMessageMineSweeper() );
    	
    	sb.append(  "  TPS: " )
    		.append( Prison.get().getPrisonTPS().getAverageTPSFormatted() );
    	
    	return sb.toString();
    }

    public String statsMessageMineSweeper() {
    	StringBuilder sb = new StringBuilder();
    	
    	if ( getStatsMineSweeperTaskMs().size() > 0 ) {
    		sb.append( " &3 MineSweeper ms: " );
    		
    		for ( Long sweepMs : getStatsMineSweeperTaskMs() ) {
    			sb.append( sweepMs ).append( " " );
			}
    	}
    	
    	return sb.toString();
    }
    
    private void resetStats() {
    	setResetPage( 0 ); 
    	
    	setBlockBreakCount( 0 );
    	
		// The reset position is critical in ensuring that all blocks within the mine are reset 
		// and that when a reset process pages (allows another process to run) then it will be
		// used to pick up where it left off.
    	setResetPosition( 0 );
    	
    	setSkipResetBypassCount( 0 );
    	
    	// NOTE: DO NOT reset blockBreakCount here!  Players can break many blocks between
    	//       here and when the mine actually starts to reset.
    	
    	setAirCountOriginal( 9 );
    	setAirCount( 0 );

    	setStatsResetTimeMS( 0 );
    	setStatsBlockGenTimeMS( 0 );
    	setStatsBlockUpdateTimeMS( 0 );
//    	setStatsTeleport1TimeMS( 0 );
//    	setStatsTeleport2TimeMS( 0 );
//    	setStatsMessageBroadcastTimeMS( 0 );
    	
    	setStatsResetPages( 0 );
    	setStatsResetPageBlocks( 0 );
		setStatsResetPageMs( 0 );
		
		
		// Save the if there are unsaved blocks:
		saveIfUnsavedBlockCounts();
		
		
		// Mine Sweeper counts:
		getStatsMineSweeperTaskMs().clear();
		
    }
    
    public void saveIfUnsavedBlockCounts() {
		if ( hasUnsavedBlockCounts() ) {
			PrisonMines.getInstance().getMineManager().saveMine( (Mine) this );

			resetUnsavedBlockCounts();
		}
    }
	

    protected abstract long teleportAllPlayersOut();
    
    
    public abstract void teleportPlayerOut(Player player);
    

    public abstract Location teleportPlayerOut(Player player, String targetLocation);


	public abstract Location alternativeTpLocation();
	
	
	public abstract void submitTeleportGlassBlockRemoval();
	
	
    /**
     * This should be used to submit async tasks.
     * 
     * @param callbackAsync
     */
    public abstract void submitAsyncTask( PrisonRunnable callbackAsync );
    
    public abstract void submitSyncTask( PrisonRunnable callbackSync );


	
	
    protected abstract void broadcastResetMessageToAllPlayersWithRadius();
    
    protected abstract void broadcastPendingResetMessageToAllPlayersWithRadius(MineJob mineJob);
    
  
	
	public int getPlayerCount() {
		int count = 0;
		
		if ( !isVirtual() && isEnabled() ) {

			World world = getWorld().get();
			
			List<Player> players = (world.getPlayers() != null ? world.getPlayers() : 
				Prison.get().getPlatform().getOnlinePlayers());
			for (Player player : players) {
				if ( getBounds().withinIncludeTopBottomOfMine(player.getLocation()) ) {
					count++;
				}
			}
		}
		
		return count;
	}
    


    /**
     * <p>This should now be ran before any mine reset. This generates the list prior to placing
     * any blocks so as to ensure the constraints are honored.  It's easier to update the
     * list that this generates, than to update the blocks in the wild after the fact.  Not 
     * to mention this will be far faster.
     * </p>
     * 
     * <p>This generation of a new block list for the mines is designed to run asynchronously. 
     * It not only generates what each block should be, it also records what the block location 
     * is.  This allows actual block updates to be performed linearly using the mineTargetBlock
     * List, or to randomly access each block using the mineTargetBlockMap; two keys in to the 
     * same collection of target blocks.
     * </p>
     * 
     * <p>The major use of the mineTargetBlock List is to allow paging of the updates: where a mine
     * can be updated in smaller segments.  The actual update must be ran synchronously and in small
     * segments.  
     * </p>
     * 
     * <p>Set the Y plane to refresh at the top and work its way down.  That way if the play is 
     * teleported to the top, it will appear like the whole mine has reset instantly and they will 
     * not see the delay from the bottom of the mine working up to the top.  This will also reduce
     * the likelihood of the player falling back in to the mine if there is no spawn set.
     * </p>
     * 
     */
    public void generateBlockListAsync() {
		
    	if ( isVirtual() || isDeleted() ) {
    		// ignore and generate no error messages:
    		return;
    	}
		if ( !isEnabled() ) {
			Output.get().logError(
					String.format( "MineReset: Block count failure: Mine is not enabled. " +
							"Ensure world exists. mine= %s ", 
							getName()  ));
			return;
		}
		
    	long start = System.currentTimeMillis();
		
		// Reset stats:
		resetStats();
		
		Random random = new Random();
		
		// Clear the mineTargetBlocks List and Map:
		clearMineTargetPrisonBlocks();

		
		// Reset the resetCounts on all blocks within the mine:
		resetResetBlockCounts();
		
		
		// setup the monitoring of the blocks that have constraints:
		List<PrisonBlockStatusData> constrainedBlocks = null;
		
		
		
		int airCount = 0;
		int currentLevel = 0;
		
		
		int yMin = getBounds().getyBlockMin();
		int yMax = getBounds().getyBlockMax();
		
		int xMin = getBounds().getxBlockMin();
		int xMax = getBounds().getxBlockMax();
		
		int zMin = getBounds().getzBlockMin();
		int zMax = getBounds().getzBlockMax();
		
		
		// The reset takes place first with the top-most layer since most mines may have
		// the player enter from the top, and the reset will appear to be more "instant".
		for (int y = yMax; y >= yMin; y--) {
			currentLevel++; // One based: First layer is currentLevel == 1
			for (int x = xMin; x <= xMax; x++) {
				for (int z = zMin; z <= zMax; z++) {
					
					
					boolean xEdge = x == xMin || x == xMax;
					boolean yEdge = y == yMin || y == yMax;
					boolean zEdge = z == zMin || z == zMax;
					
					boolean isEdge = xEdge && yEdge || xEdge && zEdge ||
									 yEdge && zEdge;
					

//					MineTargetBlock mtb = null;
					
					if ( isUseNewBlockModel() ) {
						
						// track the constraints:
						trackConstraints( currentLevel, constrainedBlocks );
						
						PrisonBlock prisonBlock = randomlySelectPrisonBlock( random, currentLevel );
						
						// Increment the mine's block count. This block is one of the control blocks:
						incrementResetBlockCount( prisonBlock );
						
						addMineTargetPrisonBlock( prisonBlock, x, y, z, isEdge );
//						mtb = new MineTargetPrisonBlock( prisonBlock, x, y, z);
						
						if ( prisonBlock.equals( PrisonBlock.AIR ) ) {
//						mAirBlocks[i++] = true;
							airCount++;
						}
					}
					else {
						
						
						BlockOld tBlock = randomlySelectBlock( random, currentLevel );
						
						// Increment the mine's block count. This block is one of the control blocks:
						incrementResetBlockCount( tBlock );
						
						addMineTargetPrisonBlock( tBlock, x, y, z, isEdge );
//						mtb = new MineTargetBlock( tBlock.getType(), x, y, z);

						if ( tBlock.equals( BlockOld.AIR ) ) {
//							mAirBlocks[i++] = true;
							airCount++;
						}
					}
					
				}
			}
		}
		

		
		setAirCountOriginal( airCount );
		setAirCount( airCount );

		
		// Apply the constraints
		constraintsApplyMin();
		
		
		// The reset position is critical in ensuring that all blocks within the mine are reset 
		// and that when a reset process pages (allows another process to run) then it will be
		// used to pick up where it left off.
		setResetPosition( 0 );
		
		long stop = System.currentTimeMillis();
		setStatsBlockGenTimeMS( stop - start );
		
    }
    
    private void trackConstraints( int currentLevel, List<PrisonBlockStatusData> constrainedBlocks )
	{
    	
    	// If the constrainedBlocks list has not be configured, set it up with the 
    	// blocks that have constraints:
		if ( constrainedBlocks == null ) {
			
			constrainedBlocks = new ArrayList<>();
			
			for (PrisonBlock block : getPrisonBlocks()) {
				if ( block.getConstraintExcludeTopLayers() > 0 || 
						block.getConstraintExcludeBottomLayers() > 0 ) {
					
					constrainedBlocks.add( block );
				}
			}
		}
		

		// If there are any constrained blocks, then need to record 
		for ( PrisonBlockStatusData block : constrainedBlocks ) {
			
			// If exclude top layers is enabled, then only try to set the 
			// rangeBlockCountLowLimit once since we need the lowest possible 
			// value.  The inital value for getRangeBlockCountLowLimit is -1.
			if ( block.getConstraintExcludeTopLayers() > 0 && 
					block.getRangeBlockCountLowLimit() <= 0 &&
					currentLevel > block.getConstraintExcludeTopLayers() ) {
				
				int targetBlockPosition = getMineTargetPrisonBlocks().size();
				block.setRangeBlockCountLowLimit( targetBlockPosition );
			}
			
			// If exclude bottom layer is enabled, then we need to track every number
			// until the currentLevel exceeds the getConstraintExcludeBottomLayers value.
			// If exclude top layers, then do not record for the bottom layers until 
			// the top layers is cleared.
			if ( (block.getConstraintExcludeTopLayers() > 0 && 
					currentLevel > block.getConstraintExcludeTopLayers() ||
					block.getConstraintExcludeTopLayers() == 0) &&
					
					block.getConstraintExcludeBottomLayers() > 0 && 
					block.getConstraintExcludeBottomLayers() < currentLevel 
					) { 
				
				int targetBlockPosition = getMineTargetPrisonBlocks().size();
				block.setRangeBlockCountHighLimit( targetBlockPosition );
				
			}
		}
		
	}

	/**
	 * 
	 * <p>Update 2021-08-25 : This function should only be called once now.  The main 
	 * work on performing the actual resets is now performed within the task
	 * MinePagedResetAsyncTask.  This is now to be ran asynchronously.
	 * </p>
	 * 
     * <p>Yeah I know, it has async in the name of the function, but it still can only
     * be ran synchronously.  The async part implies this is the reset "part" for the
     * async workflow.
     * </p>
     * 
     * <p>Before this part is ran, the generateBlockListAsync() function must be ran
     * to regenerate the new block list.
     * </p>
     *  
     */
    protected void xresetAsynchonously() {
    	boolean canceled = false;
    	
//		Output.get().logInfo( "MineRest.resetAsynchonously() " + getName() );

    	if ( isVirtual() ) {
    		canceled = true;
    	}
    	
    	if ( !canceled && getResetPage() == 0 ) {
    		generateBlockListAsync();   		
    		
    		canceled = resetAsynchonouslyInitiate();
    	}
    	
    	if ( !canceled ) {
    		
//    		// First time through... reset the block break count and run the before reset commands:
//    		if ( getResetPosition() == 0 ) {
//    			
//    			// Reset the block break count before resetting the blocks:
//    			// Set it to the original air count, if subtracted from total block count
//    			// in the mine, then the result will be blocks remaining.
//         		setBlockBreakCount( getAirCountOriginal() );
//         		
//         		
//         		if ( !getCurrentJob().getResetActions().contains( MineResetActions.NO_COMMANDS )) {
//         			
//         			// Before reset commands:
//         			if ( getResetCommands() != null && getResetCommands().size() > 0 ) {
//         				
//         				for (String command : getResetCommands() ) {
//// 	        		String formatted = cmd.replace("{player}", prisonPlayer.getName())
//// 	        				.replace("{player_uid}", player.uid.toString());
//         					if ( command.startsWith( "before: " )) {
//         						String cmd = command.replace( "before: ", "" );
//         						
//         						PrisonCommandTask cmdTask = new PrisonCommandTask( "MineReset: Before:" );
//         						cmdTask.submitCommandTask( cmd );
//         							
//         						// PrisonAPI.dispatchCommand(cmd);
//         					}
//         				}
//         			}
//         		}
//         		
//    		}

    		asynchronouslyResetSetup();
			
    		MinePagedResetAsyncTask resetTask = new MinePagedResetAsyncTask( (Mine) this, MineResetType.normal );
    		resetTask.submitTaskAsync();
    		
    		asynchronouslyResetFinalize();
    		
    		
//    		resetAsynchonouslyUpdate( true );
    		
//    		if ( getResetPosition() == getMineTargetPrisonBlocks().size() ) {
//    			// Done resetting the mine... wrap up:
//    			
//    			
//        		// If a player falls back in to the mine before it is fully done being reset, 
//        		// such as could happen if there is lag or a lot going on within the server, 
//        		// this will TP anyone out who would otherwise suffocate.  I hope! lol
//    			teleportAllPlayersOut();
////    			setStatsTeleport2TimeMS(
////    					teleportAllPlayersOut( getBounds().getyBlockMax() ) );
//        		
//        		// Reset the paging for the next reset:
//        		setResetPage( 0 );
//        		
//        		incrementResetCount();
//        		
//        		if ( !getCurrentJob().getResetActions().contains( MineResetActions.NO_COMMANDS )) {
//        			
//        			// After reset commands:
//        			if ( getResetCommands() != null && getResetCommands().size() > 0 ) {
//        				
//        				for (String command : getResetCommands() ) {
////    	        		String formatted = cmd.replace("{player}", prisonPlayer.getName())
////    	        				.replace("{player_uid}", player.uid.toString());
//        					if ( command.startsWith( "after: " )) {
//        						String cmd = command.replace( "after: ", "" );
//        						
//         						PrisonCommandTask cmdTask = new PrisonCommandTask( "MineReset: After:" );
//         						cmdTask.submitCommandTask( cmd );
//
//        						// PrisonAPI.dispatchCommand(cmd);
//        					}
//        				}
//        			}
//        		}
//    	        
//        		
//        		// Broadcast message to all players within a certain radius of this mine:
//        		broadcastResetMessageToAllPlayersWithRadius();
////        		broadcastResetMessageToAllPlayersWithRadius( MINE_RESET__BROADCAST_RADIUS_BLOCKS );
//        		
//        		
//        		submitTeleportGlassBlockRemoval();
//        		
//                
//                // Tie to the command stats mode so it logs it if stats are enabled:
//                if ( PrisonMines.getInstance().getMineManager().isMineStats() || 
//                		getCurrentJob().getResetActions().contains( MineResetActions.DETAILS ) ) {
//                	DecimalFormat dFmt = new DecimalFormat("#,##0");
//                	Output.get().logInfo("&cMine reset: &7" + getTag() + 
//                			"&c  Blocks: &7" + dFmt.format( getBounds().getTotalBlockCount() ) + 
//                			statsMessage() );
//                }
//                
//    			// If part of a chained_resets, then kick off the next reset:
//    			if ( getCurrentJob().getResetActions().contains( MineResetActions.CHAINED_RESETS )) {
//    				
//    				PrisonMines pMines = PrisonMines.getInstance();
//    				pMines.resetAllMinesNext();
//    			}
//    			
//    			
//    		}

// NOTE: Only run this ONCE now...  MinePagedResetAsyncTask handles the paging now   		
//    		else {
//    			
//    			// Need to continue to reset the mine. Resubmit it to run again.
//    			MineResetAsyncResubmitTask mrAsyncRT = new MineResetAsyncResubmitTask( this, null, 
//    					getCurrentJob().getResetActions() );
//    			
//    	    	// Must run synchronously!!
//    	    	submitSyncTask( mrAsyncRT );
//    		}
    	}
    	
    	
    }
    
    public void asynchronouslyResetSetup() {
    	
		// Reset the block break count before resetting the blocks:
		// Set it to the original air count, if subtracted from total block count
		// in the mine, then the result will be blocks remaining.
 		setBlockBreakCount( getAirCountOriginal() );
 		
 		
 		if ( !getCurrentJob().getResetActions().contains( MineResetActions.NO_COMMANDS )) {
 			
 			// Before reset commands:
 			if ( getResetCommands() != null && getResetCommands().size() > 0 ) {
 				
 				for (String command : getResetCommands() ) {
//     		String formatted = cmd.replace("{player}", prisonPlayer.getName())
//     				.replace("{player_uid}", player.uid.toString());
 					if ( command.startsWith( "before: " )) {
 						String cmd = command.replace( "before: ", "" );
 						
 						PrisonCommandTask cmdTask = new PrisonCommandTask( "MineReset: Before:" );
 						cmdTask.submitCommandTask( cmd );
 							
 						// PrisonAPI.dispatchCommand(cmd);
 					}
 				}
 			}
 		}
    }

    public void asynchronouslyResetFinalize() {
		// If a player falls back in to the mine before it is fully done being reset, 
		// such as could happen if there is lag or a lot going on within the server, 
		// this will TP anyone out who would otherwise suffocate.  I hope! lol
    	
    	MineTeleportTask teleportTask = new MineTeleportTask( (Mine) this );
		teleportTask.submitTaskSync();
		
//		teleportAllPlayersOut();
//		setStatsTeleport2TimeMS(
//				teleportAllPlayersOut( getBounds().getyBlockMax() ) );
		
		// Reset the paging for the next reset:
		setResetPage( 0 );
		
		incrementResetCount();
		
		if ( !getCurrentJob().getResetActions().contains( MineResetActions.NO_COMMANDS )) {
			
			// After reset commands:
			if ( getResetCommands() != null && getResetCommands().size() > 0 ) {
				
				for (String command : getResetCommands() ) {
//        		String formatted = cmd.replace("{player}", prisonPlayer.getName())
//        				.replace("{player_uid}", player.uid.toString());
					if ( command.startsWith( "after: " )) {
						String cmd = command.replace( "after: ", "" );
						
 						PrisonCommandTask cmdTask = new PrisonCommandTask( "MineReset: After:" );
 						cmdTask.submitCommandTask( cmd );

						// PrisonAPI.dispatchCommand(cmd);
					}
				}
			}
		}
        
		
		// Broadcast message to all players within a certain radius of this mine:
		broadcastResetMessageToAllPlayersWithRadius();
//		broadcastResetMessageToAllPlayersWithRadius( MINE_RESET__BROADCAST_RADIUS_BLOCKS );
		
		
		submitTeleportGlassBlockRemoval();
		
        
        // Tie to the command stats mode so it logs it if stats are enabled:
        if ( PrisonMines.getInstance().getMineManager().isMineStats() || 
        		getCurrentJob().getResetActions().contains( MineResetActions.DETAILS ) ) {
        	DecimalFormat dFmt = new DecimalFormat("#,##0");
        	Output.get().logInfo("&cMine reset: &7" + getTag() + 
        			"&c  Blocks: &7" + dFmt.format( getBounds().getTotalBlockCount() ) + 
        			statsMessage() );
        }
        
		// If part of a chained_resets, then kick off the next reset:
		if ( getCurrentJob().getResetActions().contains( MineResetActions.CHAINED_RESETS )) {
			
			PrisonMines pMines = PrisonMines.getInstance();
			pMines.resetAllMinesNext();
		}

    }
    
    public boolean resetAsynchonouslyInitiate() {
    	boolean canceled = false;
		
    	if ( isVirtual()) {
    		canceled = true;
    	}
    	else 
		if ( !isEnabled() ) {
			Output.get().logError(
					String.format( "MineReset: resetAsynchonouslyInitiate failure: Mine is not enabled. " +
							"Ensure world exists. mine= %s ", 
							getName()  ));
			canceled = true;
		}
		else {
//			long start = System.currentTimeMillis();
			
			// The all-important event
			MineResetEvent event = new MineResetEvent(this);
			Prison.get().getEventBus().post(event);
			
			canceled = event.isCanceled();
			if (!canceled) {
				
				MineTeleportTask teleportTask = new MineTeleportTask( (Mine) this );
				teleportTask.submitTaskSync();
//				try {
//					teleportAllPlayersOut();
////					setStatsTeleport1TimeMS(
////							teleportAllPlayersOut( getBounds().getyBlockMax() ) );
//					
//				} catch (Exception e) {
//					Output.get().logError("&cMineReset: Failed to TP players out of mine. mine= " + 
//									getName(), e);
//					canceled = true;
//				}
			}
			
//			long stop = System.currentTimeMillis();
//			setStatsResetTimeMS( stop - start );
		}

    	return canceled;
    }
    

//    /**
//     * <p>This is the synchronous part of the job that actually updates the blocks.
//     * It will only replace what it can within the given allocated milliseconds,
//     * then it will terminate and allow this process to re-run, picking up where it
//     * left off.
//     * </p>
//     * 
//     * <p>Paging is what this is doing. Running, and doing what it can within it's 
//     * limited amount of time, then yielding to any other task, then resuming later.
//     * This is a way of running a massive synchronous task, without hogging all the
//     * resources and killing the TPS.
//     * </p>
//     * 
//     * <p>NOTE: The values for MINE_RESET__PAGE_TIMEOUT_CHECK__BLOCK_COUNT and for
//     * MINE_RESET__MAX_PAGE_ELASPSED_TIME_MS are set to arbitrary values and may not
//     * be the correct values.  They may be too large and may have to be adjusted to 
//     * smaller values to better tune the process.
//     * </p>
//     *  
//     */
//    private void resetAsynchonouslyUpdate( boolean paged ) {
//    	if ( isVirtual() ) {
//    		// ignore:
//    	}
//    	else
//    	if ( !isEnabled() ) {
//			Output.get().logError(
//					String.format( "MineReset: resetAsynchonouslyUpdate failure: Mine is not enabled. " +
//							"Ensure world exists. mine= %s ", 
//							getName()  ));
//		}
//		else {
//			World world = getBounds().getCenter().getWorld();
//			
//			
//			long start = System.currentTimeMillis();
//			
////			boolean isFillMode = PrisonMines.getInstance().getConfig().fillMode;
//			
//			int blocksPlaced = 0;
//			long elapsed = 0;
//			
//			int i = getResetPosition();
//			for ( ; i < getMineTargetPrisonBlocks().size(); i++ )
//			{
//				MineTargetPrisonBlock target = getMineTargetPrisonBlocks().get(i);
//				
//				Location targetBlock = new Location(world, 
//						target.getBlockKey().getX(), target.getBlockKey().getY(), 
//						target.getBlockKey().getZ());
//				
////				if (!isFillMode || isFillMode && targetBlock.getBlockAt().isEmpty()) {
////				} 
//				if ( isUseNewBlockModel() ) {
//					
//					targetBlock.getBlockAt().setPrisonBlock( (PrisonBlock) target.getPrisonBlock() );
//				}
//				else {
//					
//					targetBlock.getBlockAt().setType( ((BlockOld) target.getPrisonBlock()).getType() );
//				}
//				
//				/**
//				 * If paged is enabled... 
//				 * 
//				 * About every 250 blocks, or so, check to see if the current wall time 
//				 * spent is greater than
//				 * the threshold.  If it is greater, then end the update and let it resubmit.  
//				 * It does not matter how many blocks were actually updated during this "page", 
//				 * but what it is more important is the actual elapsed time.  This is to allow other
//				 * processes to get processing time and to eliminate possible lagging.
//				 */
//				if ( paged && i % getResetPageTimeoutCheckBlockCount() == 0 ) {
//					elapsed = System.currentTimeMillis() - start;
//					if ( elapsed > getResetPageMaxPageElapsedTimeMs() ) {
//
//						break;
//					}
//				}
//			}
//			
//			blocksPlaced = i - getResetPosition();
//			
//			if ( PrisonMines.getInstance().getMineManager().isMineStats() ) {
//				
//				// Only print these details if stats is enabled:
//				Output.get().logInfo( "MineReset.resetAsynchonouslyUpdate() :" +
//						" page " + getResetPage() + 
//						"  blocks = " + blocksPlaced + "  elapsed = " + elapsed + 
//						" ms  TPS: " + Prison.get().getPrisonTPS().getAverageTPSFormatted() );
//			}
//
//			setResetPosition( i );
//			
//			setResetPage( getResetPage() + 1 );
//			
//			long time = System.currentTimeMillis() - start;
//			setStatsBlockUpdateTimeMS( time + getStatsBlockUpdateTimeMS() );
//			setStatsResetTimeMS( time + getStatsResetTimeMS() );
//			
//			
//			setStatsResetPages( getStatsResetPages() + 1 );
//			setStatsResetPageBlocks( getStatsResetPageBlocks() + blocksPlaced );
//			setStatsResetPageMs( getStatsResetPageMs() + time  );
//		}
//
//    }
    


    /**
     * This task should be ran upon loading of the mines upon server start.  
     * This will calculate how many air blocks there are within the mine, which
     * will be what the blockBreakCount should be set to initially. 
     * 
     * But as a warning, this may trigger stack traces if there are active
     * entities in the unloaded chunks.  May have run this synchronously. :(
     */
    public void refreshBlockBreakCountUponStartup() {
    	
    	// if the mine is being used in a unit test, then it will not have a value for 
    	// bounds and therefore do not run the task.
    	if ( getBounds() != null ) {
    		
    		OnStartupRefreshBlockBreakCountAsyncTask cabAsyncTask = new OnStartupRefreshBlockBreakCountAsyncTask(this);
    		
    		// Must run synchronously!!
    		submitSyncTask( cabAsyncTask );
    		//submitAsyncTask( cabAsyncTask );
    	}
    }
    
//    protected void resetAirCountStartupAsyncTask() {
//    	refreshAirCountAsyncTask( false );
//    }
    
    /**
     * <p>This function performs the air count and should be ran as an async task.
     * </p>
     * 
     * <p>This also should be used to generate the targetBlockList to help identify
     * unknown blocks when being processed.
     * </p>
     * 
     * <p>WARNING: This generates a ton of async failures upon startup or during other
     * times.  This MUST be ran synchronously...
     * </p>
     * 
     */
	protected void refreshAirCountAsyncTask()
	{
    	
    	if ( isVirtual() ) {
    		// ignore:
    	}
    	else 
    	if ( !isEnabled() ) {
			Output.get().logError(
					String.format( "MineReset: refreshAirCountAsyncTask failure: Mine is not enabled. " +
							"Ensure world exists. mine= %s ", 
							getName()  ));
		}
		else if ( isUseNewBlockModel() &&
				getPrisonBlocks().size() == 1 && 
				getPrisonBlocks().get( 0 ).equals( PrisonBlock.IGNORE ) ) {
		
			// This mine is set to ignore all blocks when trying to do a reset, 
			// so for now ignore the types and just set air count to zero.
			// Basically, this mine, if using natural spawned landscape, may contain blocks that are
			// not registered and tracked within prison, and hence will report incorrect errors.
			setAirCount( 0 );
		}
		else if ( !isUseNewBlockModel() &&
				getBlocks().size() == 1 && 
				getBlocks().get( 0 ).getType() == BlockType.IGNORE ) {
			
			// This mine is set to ignore all blocks when trying to do a reset, 
			// so for now ignore the types and just set air count to zero.
			// Basically, this mine, if using natural spawned landscape, may contain blocks that are
			// not registered and tracked within prison, and hence will report incorrect errors.
			setAirCount( 0 );
		}
		else {
			long start = System.currentTimeMillis();
			Optional<World> worldOptional = getWorld();
			World world = worldOptional.get();
			
			
			// Reset the target block lists:
			clearMineTargetPrisonBlocks();
			
			
			
			int airCount = 0;
			int errorCount = 0;
			
			
			
			int yMin = getBounds().getyBlockMin();
			int yMax = getBounds().getyBlockMax();
			
			int xMin = getBounds().getxBlockMin();
			int xMax = getBounds().getxBlockMax();
			
			int zMin = getBounds().getzBlockMin();
			int zMax = getBounds().getzBlockMax();
			
			
			
			StringBuilder sb = new StringBuilder();
			
			for (int y = yMax; y >= yMin; y--) {
				for (int x = xMin; x <= xMax; x++) {
					for (int z = zMin; z <= zMax; z++) {
						
						try {
							Location targetBlock = new Location(world, x, y, z);
							Block tBlock = targetBlock.getBlockAt();
							
							
							boolean xEdge = x == xMin || x == xMax;
							boolean yEdge = y == yMin || y == yMax;
							boolean zEdge = z == zMin || z == zMax;
							
							boolean isEdge = xEdge && yEdge || xEdge && zEdge ||
											 yEdge && zEdge;
							
							
							if ( isUseNewBlockModel() ) {
								
								PrisonBlock pBlock = tBlock.getPrisonBlock();

								// Increment the mine's block count. This block is one of the control blocks:
								addMineTargetPrisonBlock( incrementResetBlockCount( pBlock ), x, y, z, isEdge );
								
								
								if ( pBlock == null ||
										pBlock.equals( PrisonBlock.AIR ) ) {
									airCount++;
								}
							}
							else {
								
								BlockOld oBlock = new BlockOld( tBlock.getType() );

								// Increment the mine's block count. This block is one of the control blocks:
								addMineTargetPrisonBlock( incrementResetBlockCount( oBlock ), x, y, z, isEdge );
								
								
								if ( tBlock.getType() == BlockType.AIR ) {
									airCount++;
								}
							}
						}
						catch ( Exception e ) {
							// Updates to the "world" should never be ran async.  Upon review of the above 
							// that gets the location and block, causes the chunk to load, if it is not loaded,
							// and if there is an entity in that loaded chunk it will throw an exception:
							//     java.lang.IllegalStateException: Asynchronous entity world add!
							// If there are no entities, it will be fine, but they could cause issues with async 
							// access of unloaded chunks.
							String coords = String.format( "%d.%d.%d ", x, y, z );
							if ( errorCount ++ == 0 ) {
								String message = String.format( 
										"MineReset.refreshAirCountAsyncTask: Error counting air blocks: " +
												"Mine=%s coords=%s  Error: %s ", getName(), coords, e.getMessage() );
								if ( e.getMessage() != null && e.getMessage().contains( "Asynchronous entity world add" )) {
									Output.get().logWarn( message );
								} else {
									Output.get().logWarn( message, e );
								}
								
							} 
							else if ( errorCount <= 20 ) {
								sb.append( coords );
							}
						}
					}
				}
			}
			
			if ( errorCount > 0 ) {
				String message = String.format( 
						"MineReset.refreshAirCountAsyncTask: Error counting air blocks: Mine=%s: " +
								"errorCount=%d  blocks%s : %s", getName(), errorCount,
								(errorCount > 20 ? "(first 20)" : ""),
								sb.toString() );
				Output.get().logWarn( message );
			}
			
			
			setAirCount( airCount );
			
			long stop = System.currentTimeMillis();
			long elapsed = stop - start;
			setAirCountElapsedTimeMs( elapsed );
			setAirCountTimestamp( stop );
		}
		
	}
	
	
	/**
	 * <p>This function should ONLY be used if an enchantment plugin is being used that cannot
	 * provide a working explosion event to monitor, and the plugin is breaking more blocks than what
	 * is being reported with the BlockBreakEvent.  
	 * </p>
	 * 
	 * <p>This function ignores previously counted blocks, which is indicated with isAirBroke().
	 * Since it only processes blocks that have not been identified as being broke, then whenever
	 * it encounters an air block (block.isEmpty()) then it is understood that block was just 
	 * broke and needs to be counted then the related targetBlock needs to be set as <b>air</b> and as
	 * have been <b>broke</b>.
	 * </p>
	 */
	protected void runMineSweeperTask() {
		
		World world = getBounds().getCenter().getWorld();
		if ( world != null ) {

			long start = System.currentTimeMillis();
			int blocksChanged = 0;
			
			for ( MineTargetPrisonBlock targetBlock : getMineTargetPrisonBlocks() ) {
				
				if ( targetBlock != null && !targetBlock.isAirBroke() ) {
					MineTargetBlockKey key = targetBlock.getBlockKey();
					Location blockLocation = new Location( world, key.getX(), key.getY(), key.getZ() );
					
					Block block = world.getBlockAt( blockLocation );
					if ( block.isEmpty() ) {
						
						targetBlock.getPrisonBlock().incrementMiningBlockCount();
						targetBlock.setAirBroke( true );
						
						blocksChanged++;
					}
				}
			}
			

			long stop = System.currentTimeMillis();
			long elapsed = stop - start;
			
			// This ensures that the getRemainingBlockCount() is updated and is correct:
			addBlockBreakCount( blocksChanged );
			
			getStatsMineSweeperTaskMs().add( elapsed );
			
			if ( getStatsMineSweeperTaskMs().size() > 10 ) {
				getStatsMineSweeperTaskMs().remove( 0 );
			}
			
			setMineSweeperTotalMs( elapsed + getMineSweeperTotalMs() );
			setMineSweeperCount( 1 + getMineSweeperCount() );
			setMineSweeperBlocksChanged( blocksChanged + getMineSweeperBlocksChanged() );
			
			// Unlock this task so more can be submitted:
			synchronized ( MineSweeperTask.class ) {
				setMineSweeperSubmitted( false );
			}
			
			
			// Check to see if a mine reset is needed:
			checkZeroBlockReset();
		}
		
	}
	
	
	/**
	 * <p>This function, submitMineSweeperTask(), will only submit itself if the boolean 
	 * value isMineSweeperSubmitted() is not set.  This function will calculate a submission
	 * delay that will range from 2 seconds to 10 seconds.  The variation of 8 seconds is 
	 * based upon the percent remaining blocks in the mine, the less that remains, then
	 * the shorter the delay.
	 * </p>
	 * 
	 * @return if true, then the task was just submitted, if false another task is running.
	 */
	public boolean submitMineSweeperTask() {
		boolean results = false;
		
		if ( isMineSweeperEnabled() && !isMineSweeperSubmitted() ) {
			
			synchronized ( MineSweeperTask.class ) {
				
				if ( !isMineSweeperSubmitted() ) {
					
					setMineSweeperSubmitted( true );
					
					
					// calculate the refresh interval, which is actually the submission delay for 
					// running the task.  The ranks of the interval should be between 2 seconds 
					// and 15 seconds.
					int blockCount = getBounds().getTotalBlockCount();
					int blockCurrent = getRemainingBlockCount();
					double blockPercent = blockCurrent / ((double) blockCount);
					
					double taskDelaySec = 2.0d + (13.0d * blockPercent );
					
					long taskDelayTicks = (long) (taskDelaySec * 20.0d);
					
					MineSweeperTask mineSweeperTask = new MineSweeperTask(this, null);
					
					
					// Must run synchronously!!
					PrisonTaskSubmitter.runTaskLater( mineSweeperTask, taskDelayTicks );
					
					
					results = true;
					
				}
			}
		}
		
		return results;
	}
    
	public int getRemainingBlockCount() {
		int remainingBlocks = getBounds().getTotalBlockCount() - getBlockBreakCount();
//		int remainingBlocks = getBounds().getTotalBlockCount() - getAirCount();
		return remainingBlocks;
	}
	
	public double getPercentRemainingBlockCount() {
		if ( isVirtual() ) {
			return 0;
		}
		int totalCount = getBounds().getTotalBlockCount();
		int remainingCount = getRemainingBlockCount();
		double percentRemaining = (totalCount == 0d ? 0d : (remainingCount * 100d) / (double) totalCount);
//		double remainingBlocksP = (totalCount - getAirCount()) * 100d;
//		double originalCount = totalCount - getAirCountOriginal();
//		double percentRemaining = (originalCount == 0d ? 0d : remainingBlocksP / originalCount);
		return percentRemaining;
	}
	
	/**
	 * <p>This assumes the air block count was just updated.
	 * </p>
	 * 
	 */
	public void refreshMineAsyncTask() {
		if ( isSkipResetEnabled() ) {
			
			if ( getAirCountOriginal() == getAirCount() ) {
				// No blocks mined. Skip and do not increment bypass count since mine is pristine.
				// Skip Reset!!
				return;
			}
			else if ( getPercentRemainingBlockCount() > getSkipResetPercent() ) {
				// Blocks have been mined, but not enough to reset the mine yet.
				// Increment skip bypass count.
				setSkipResetBypassCount( getSkipResetBypassCount() + 1 );
				
				if ( getSkipResetBypassCount() < getSkipResetBypassLimit() ) {
					// Skip Reset!!
					return;
				}
				
				// Reached bypass limit. Must reset!
				setSkipResetBypassCount( 0 );
			}
			
			// Must reset. Not bypassing.
		}
		
		// Mine reset here:
		// Async if possible...
		
		MinePagedResetAsyncTask resetTask = new MinePagedResetAsyncTask( (Mine) this, MineResetType.normal );
		resetTask.submitTaskAsync();
		
//		resetAsynchonously();
	}
	
//	public void refreshMineAsyncResubmitTask() {
//		
//		// Mine reset here:
//		resetAsynchonously();
//	}
	
	

	private PrisonBlock randomlySelectPrisonBlock( Random random, int currentLevel ) {
		
		int targetBlockPosition = getMineTargetPrisonBlocks().size();
		
		PrisonBlock prisonBlock = Prison.get().getPlatform().getPrisonBlock( "AIR" );
		
		// If a chosen block was skipped, try to find another block, but try no more than 10 times
		// to prevent a possible endless loop.  Side effects of failing to find a block in 10 attempts
		// would be an air block.
		boolean success = false;
		int attempts = 0;
		while ( !success && attempts++ < 10 ) {
			double chance = random.nextDouble() * 100.0d;
			
			for (PrisonBlock block : getPrisonBlocks()) {
				boolean isBlockEnabled = block.isBlockConstraintsEnbled( currentLevel, targetBlockPosition );
				
				if ( chance <= block.getChance()  ) {
					
					// If this block is chosen and it was not skipped, then use this block and exit.
					// Otherwise the chance will be recalculated and tried again to find a valid block,
					// since the odds have been thrown off...
					if ( isBlockEnabled ) {
						prisonBlock = block;
						
						// stop trying to locate a block so success will terminate the search:
						success = true;
					}
					
					break;
				} else {
					chance -= block.getChance();
				}
			}
		}
		return prisonBlock;
	}
	
	private BlockOld randomlySelectBlock( Random random, int currentLevel ) {
		
		int targetBlockPosition = getMineTargetPrisonBlocks().size();

		BlockOld results = BlockOld.AIR;
		
		// If a chosen block was skipped, try to find another block, but try no more than 10 times
		// to prevent a possible endless loop.  Side effects of failing to find a block in 10 attempts
		// would be an air block.
		boolean success = false;
		int attempts = 0;
		while ( !success && attempts++ < 10 ) {
			double chance = random.nextDouble() * 100.0d;
			
			for (BlockOld block : getBlocks()) {
				boolean isBlockEnabled = block.isBlockConstraintsEnbled( currentLevel, targetBlockPosition );
				
				if ( chance <= block.getChance()  ) {
					
					// If this block is chosen and it was not skipped, then use this block and exit.
					// Otherwise the chance will be recalculated and tried again to find a valid block,
					// since the odds have been thrown off...
					if ( isBlockEnabled ) {
						results = block;
						
						// stop trying to locate a block so success will terminate the search:
						success = true;
					}
					
					break;
				} else {
					chance -= block.getChance();
				}
			}
		}

		
//		for (BlockOld block : getBlocks()) {
//			if (block.checkConstraints( currentLevel, targetBlockPosition ) &&
//					chance <= block.getChance() && 
//		    		(block.getConstraintMax() == 0 || block.getResetBlockCount() < block.getConstraintMax())) {
//				results = block;
//				break;
//			} else {
//				chance -= block.getChance();
//			}
//		}
		return results;
	}
    
	
	private void constraintsApplyMin() {
		
    	if ( isUseNewBlockModel() ) {
    		
    		for ( PrisonBlockStatusData block : getPrisonBlocks() ) {
    			constraintsApplyMin( block, isUseNewBlockModel() );
    		}
    	}
    	else {
    		
    		for ( PrisonBlockStatusData block : getBlocks() ) {
    			constraintsApplyMin( block, isUseNewBlockModel() );
    		}
    	}
	}
    
  
    /**
     * <p>This function attempts to add enough blocks to the target in order to meet the
     * minimum constraint requirement. There are chances that a placement will not be 
     * successful, so this only allows it to be attempted 3 times the number of blocks 
     * to be placed to prevent an endless loop.
     * </p>
     * 
     * <p>The randomly selected block to be changed can only be changed if it does not
     * have a minimum or maximum constraint value enabled (has a value of zero for both).
     * When other constraints are added, it will also have to honor the other constraints.
     * </p>
     * 
     * @param block
     * @param useNewBlockModel
     */
    private void constraintsApplyMin( PrisonBlockStatusData block, boolean useNewBlockModel )
	{
    	if ( block.getConstraintMin() > 0 ) {
    		
    		int maxAttempts = (block.getConstraintMin() - block.getBlockPlacedCount()) * 3;
    		for ( int i = 0; i < maxAttempts && block.getBlockPlacedCount() < block.getConstraintMin(); i++ ) {
    			
//    			int maxSize = getMineTargetPrisonBlocks().size();
    			
    			int rangeLow = block.getRangeBlockCountLowLimit();
    			int rangeHigh = block.getRangeBlockCountHighLimit();
//    			int rangeLow = block.getRangeBlockCountLow();
//    			int rangeHigh = block.getRangeBlockCountHigh();
    			
    			
    			// Each block has a valid range in which it can spawn in the mine.  This range
    			// is honored by using the rangeHigh and rangeLow values.
    			int rndPos = ((int) Math.round( Math.random() * (rangeHigh - rangeLow) )) + rangeLow;
    			
    			if ( rndPos < getMineTargetPrisonBlocks().size() ) {
    				
    				MineTargetPrisonBlock targetBlock = getMineTargetPrisonBlocks().get( rndPos );
    				
    				if ( targetBlock != null && 
    						targetBlock.getPrisonBlock().getConstraintMin() == 0 &&
    						targetBlock.getPrisonBlock().getConstraintMax() == 0 &&
    						!targetBlock.getPrisonBlock().getBlockName().equalsIgnoreCase( 
    								block.getBlockName() ) ) {
    					
    					// decrement the block count on the block being removed:
    					if ( targetBlock.getPrisonBlock().isAir() ) {
    						
    						// Need to remove one from the air count fields:
    						setAirCountOriginal( getAirCountOriginal() - 1 );
    						setAirCount( getAirCount() - 1 );
    						
    					}
    					else {
    						targetBlock.getPrisonBlock().decrementResetBlockCount();
    					}
    					
    					
    					// Add the new block and increment it's count:
    					targetBlock.setPrisonBlock( block );
    					block.incrementResetBlockCount();
    				}
    			}
    		}
    	}
	}

	/**
     * This clears the mine, then provides particle tracers around the outer corners.
     */
    public void enableTracer() {
    	
    	// First clear the mine:
    	clearMine( true );

//    	Prison.get().getPlatform().enableMineTracer( 
//    				getWorldName(),
//    				getBounds().getMin(), 
//    				getBounds().getMax());

    	
    	
    }
    
    /**
     * <p>This will adjust the size of the existing mine's area.
     * Any voids left due to reductions in size will not be auto
     * filled.
     * </p>
     * 
     * @param edge
     * @param amount
     */
    public void adjustSize( Edges edge, int amount ) {
    	
    	// First clear the mine:
    	clearMine( false );
		
    	// if amount is zero, then just refresh the liner:
    	
    	if ( amount < 0 ) {
    		while ( amount++ < 0 ) {
    			
    			new MineLinerBuilder( (Mine) this, edge, LinerPatterns.repair, false );

    			Bounds newBounds = new Bounds( getBounds(), edge, -1 );
    			setBounds( newBounds );
    			
    			new MineLinerBuilder( (Mine) this, edge, LinerPatterns.repair, false );
    		}
    	}
    	else if ( amount > 0 ) {
    		new MineLinerBuilder( (Mine) this, edge, LinerPatterns.repair, false );
    		
    		Bounds newBounds = new Bounds( getBounds(), edge, amount );
    		setBounds( newBounds );
    	}

    	// Rebuild the liner if it exists:
    	for ( Edges targtEdge : Edges.values() ) {
			
    		if ( getLinerData().hasEdge( targtEdge ) ) {
    			
    			LinerPatterns pattern = LinerPatterns.fromString( getLinerData().getEdge( targtEdge ) );
    			boolean force = getLinerData().getForce( targtEdge );
    			
    			new MineLinerBuilder( (Mine) this, targtEdge, pattern, force );
    		}
		}
		
		// Finally trace the mine:
		clearMine( true );
    }
    
    
    public void moveMine( Edges edge, int amount ) {
    	
    	MineMover moveMine = new MineMover();
    	moveMine.moveMine( (Mine) this, edge, amount );
    }
    
    
    public void clearMine( boolean tracer ) {
		
    	MineTracerBuilder tracerBuilder = new MineTracerBuilder();
    	tracerBuilder.clearMine( (Mine) this, tracer );
    }
    

	public MineJob getCurrentJob()
	{
		return currentJob;
	}
	public void setCurrentJob( MineJob currentJob )
	{
		this.currentJob = currentJob;
	}
	
	
    
    private void addMineTargetPrisonBlock( PrisonBlockStatusData block, int x, int y, int z, boolean isEdge ) {
    	
    	MineTargetPrisonBlock mtpb = new MineTargetPrisonBlock( block, getWorld().get(), x, y, z, isEdge );
    	
		getMineTargetPrisonBlocks().add( mtpb );
		getMineTargetPrisonBlocksMap().put( mtpb.getBlockKey(), mtpb );
    }
    
    private void clearMineTargetPrisonBlocks() {
    	getMineTargetPrisonBlocks().clear();
    	getMineTargetPrisonBlocksMap().clear();
    }
    
    
	public List<MineTargetPrisonBlock> getMineTargetPrisonBlocks()
	{
		return mineTargetPrisonBlocks;
	}

	public TreeMap<MineTargetBlockKey, MineTargetPrisonBlock> getMineTargetPrisonBlocksMap()
	{
		return mineTargetPrisonBlocksMap;
	}
	
	public MineTargetPrisonBlock getTargetPrisonBlock( Block block ) {
		
		Location loc = block.getLocation();
		MineTargetBlockKey key = new MineTargetBlockKey( loc );
		
		return getMineTargetPrisonBlocksMap().get( key );
	}
	
	
	
//	public String getTargetPrisonBlockName( Block block ) {
//		String results = "AIR";
//		
//		if ( block != null ) {
//			PrisonBlock pBlock = block.getPrisonBlock();
//			if ( pBlock != null ) {
//				
//				results = pBlock.getBlockName();
//			}
//			
//			if ( "AIR".equalsIgnoreCase( results ) ) {
//				MineTargetPrisonBlock targetBlock = getTargetPrisonBlock( block );
//				
//				if ( targetBlock != null ) {
//					results = targetBlock.getPrisonBlock().getBlockName();
//				}
//			}
//		}
//		
//		return results;
//	}
	


	public int getResetPage()
	{
		return resetPage;
	}
	public void setResetPage( int resetPage )
	{
		this.resetPage = resetPage;
	}
	
	public int getResetPosition()
	{
		return resetPosition;
	}
	public void setResetPosition( int resetPosition )
	{
		this.resetPosition = resetPosition;
	}

	public long getResetPageMaxPageElapsedTimeMs() {
		if ( resetPageMaxPageElapsedTimeMs == -1 ) {
			this.resetPageMaxPageElapsedTimeMs = Prison.get().getPlatform()
										.getConfigLong( "prison-mines.reset-paging.max-page-elapsed-time-ms", 
													MINE_RESET__MAX_PAGE_ELASPSED_TIME_MS );
		}
		return resetPageMaxPageElapsedTimeMs;
	}
	public void setResetPageMaxPageElapsedTimeMs( long resetPageMaxPageElapsedTimeMs ) {
		this.resetPageMaxPageElapsedTimeMs = resetPageMaxPageElapsedTimeMs;
	}

	public long getResetPagePageSubmitDelayTicks() {
		if ( resetPagePageSubmitDelayTicks == -1 ) {
			this.resetPagePageSubmitDelayTicks = Prison.get().getPlatform()
										.getConfigLong( "prison-mines.reset-paging.page-submit-delay-ticks", 
													MINE_RESET__PAGE_SUBMIT_DELAY_TICKS );
		}
		return resetPagePageSubmitDelayTicks;
	}
	public void setResetPagePageSubmitDelayTicks( long resetPagePageSubmitDelayTicks ) {
		this.resetPagePageSubmitDelayTicks = resetPagePageSubmitDelayTicks;
	}

	public long getResetPageTimeoutCheckBlockCount() {
		if ( resetPageTimeoutCheckBlockCount == -1 ) {
			this.resetPageTimeoutCheckBlockCount = Prison.get().getPlatform()
										.getConfigLong( "prison-mines.reset-paging.page-timeout-check-block-count", 
												MINE_RESET__PAGE_TIMEOUT_CHECK__BLOCK_COUNT );
		}
		return resetPageTimeoutCheckBlockCount;
	}
	public void setResetPageTimeoutCheckBlockCount( long resetPageTimeoutCheckBlockCount ) {
		this.resetPageTimeoutCheckBlockCount = resetPageTimeoutCheckBlockCount;
	}

	public int getAirCountOriginal()
	{
		return airCountOriginal;
	}
	public void setAirCountOriginal( int airCountOriginal )
	{
		this.airCountOriginal = airCountOriginal;
	}

	public int getAirCount()
	{
		return airCount;
	}
	public void setAirCount( int airCount )
	{
		this.airCount = airCount;
	}

	public long getAirCountTimestamp()
	{
		return airCountTimestamp;
	}
	public void setAirCountTimestamp( long airCountTimestamp )
	{
		this.airCountTimestamp = airCountTimestamp;
	}

	public long getAirCountElapsedTimeMs()
	{
		return airCountElapsedTimeMs;
	}
	public void setAirCountElapsedTimeMs( long airCountElapsedTimeMs )
	{
		this.airCountElapsedTimeMs = airCountElapsedTimeMs;
	}

	public long getStatsResetTimeMS()
	{
		return statsResetTimeMS;
	}
	public void setStatsResetTimeMS( long statsResetTimeMS )
	{
		this.statsResetTimeMS = statsResetTimeMS;
	}

	public long getStatsBlockGenTimeMS()
	{
		return statsBlockGenTimeMS;
	}
	public void setStatsBlockGenTimeMS( long statsBlockGenTimeMS )
	{
		this.statsBlockGenTimeMS = statsBlockGenTimeMS;
	}

	public long getStatsBlockUpdateTimeMS()
	{
		return statsBlockUpdateTimeMS;
	}
	public void setStatsBlockUpdateTimeMS( long statsBlockUpdateTimeMS )
	{
		this.statsBlockUpdateTimeMS = statsBlockUpdateTimeMS;
	}

//	public long getStatsTeleport1TimeMS()
//	{
//		return statsTeleport1TimeMS;
//	}
//	public void setStatsTeleport1TimeMS( long statsTeleport1TimeMS )
//	{
//		this.statsTeleport1TimeMS = statsTeleport1TimeMS;
//	}
//
//	public long getStatsTeleport2TimeMS()
//	{
//		return statsTeleport2TimeMS;
//	}
//	public void setStatsTeleport2TimeMS( long statsTeleport2TimeMS )
//	{
//		this.statsTeleport2TimeMS = statsTeleport2TimeMS;
//	}
//
//	public long getStatsMessageBroadcastTimeMS()
//	{
//		return statsMessageBroadcastTimeMS;
//	}
//	public void setStatsMessageBroadcastTimeMS( long statsMessageBroadcastTimeMS )
//	{
//		this.statsMessageBroadcastTimeMS = statsMessageBroadcastTimeMS;
//	}

	public int getStatsResetPages() {
		return statsResetPages;
	}
	public void setStatsResetPages( int statsResetPages ) {
		this.statsResetPages = statsResetPages;
	}

	public long getStatsResetPageBlocks() {
		return statsResetPageBlocks;
	}
	public void setStatsResetPageBlocks( long statsResetPageBlocks ) {
		this.statsResetPageBlocks = statsResetPageBlocks;
	}

	public long getStatsResetPageMs() {
		return statsResetPageMs;
	}
	public void setStatsResetPageMs( long statsResetPageMs ) {
		this.statsResetPageMs = statsResetPageMs;
	}

	public List<Long> getStatsMineSweeperTaskMs() {
		return statsMineSweeperTaskMs;
	}

	public boolean isMineSweeperSubmitted() {
		return mineSweeperSubmitted;
	}
	public void setMineSweeperSubmitted( boolean mineSweeperSubmitted ) {
		this.mineSweeperSubmitted = mineSweeperSubmitted;
	}

    
}
