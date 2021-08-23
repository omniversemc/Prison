package tech.mcprison.prison.spigot.autofeatures.events;

import org.bukkit.Bukkit;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.plugin.EventExecutor;
import org.bukkit.plugin.PluginManager;

import com.vk2gpz.tokenenchant.event.TEBlockExplodeEvent;

import tech.mcprison.prison.Prison;
import tech.mcprison.prison.autofeatures.AutoFeaturesFileConfig.AutoFeatures;
import tech.mcprison.prison.output.ChatDisplay;
import tech.mcprison.prison.output.Output;
import tech.mcprison.prison.spigot.SpigotPrison;
import tech.mcprison.prison.spigot.block.OnBlockBreakEventListener.BlockBreakPriority;
import tech.mcprison.prison.spigot.game.SpigotHandlerList;

public class AutoManagerTokenEnchant 
	extends AutoManagerEventsManager {

	public AutoManagerTokenEnchant() {
        super();
        
    }
	
	
	@Override
	public void registerEvents() {
	
		initialize();
		
	}

	
    public class AutoManagerTokenEnchantEventListener
		extends AutoManagerTokenEnchant
		implements Listener {
    	
        @EventHandler(priority=EventPriority.LOW) 
        public void onTEBlockExplode(TEBlockExplodeEvent e) {
        	genericBlockExplodeEventAutoManager( e );
        }
    }
    
    public class OnBlockBreakEventTokenEnchantEventListener
	    extends AutoManagerTokenEnchant
	    implements Listener {
    	
    	@EventHandler(priority=EventPriority.NORMAL) 
    	public void onTEBlockExplode(TEBlockExplodeEvent e) {
    		genericBlockExplodeEvent( e );
    	}
    }
    
    public class OnBlockBreakEventTokenEnchantEventListenerMonitor
	    extends AutoManagerTokenEnchant
	    implements Listener {
    	
    	@EventHandler(priority=EventPriority.MONITOR) 
    	public void onTEBlockExplode(TEBlockExplodeEvent e) {
    		genericBlockExplodeEventMonitor( e );
    	}
    }
    
    @Override
    public void initialize() {
    	boolean isEventEnabled = isBoolean( AutoFeatures.isProcessTokensEnchantExplosiveEvents );
    	
    	if ( !isEventEnabled ) {
    		return;
    	}
    	
    	// Check to see if the class TEBlockExplodeEvent even exists:
    	try {
    		Output.get().logInfo( "AutoManager: checking if loaded: TokenEnchant" );
    		
    		Class.forName( "com.vk2gpz.tokenenchant.event.TEBlockExplodeEvent", false, 
    				this.getClass().getClassLoader() );
    		
    		Output.get().logInfo( "AutoManager: Trying to register TokenEnchant" );
    		
    		
    		String eP = getMessage( AutoFeatures.TokenEnchantBlockExplodeEventPriority );
    		BlockBreakPriority eventPriority = BlockBreakPriority.fromString( eP );
    		
    		if ( eventPriority != BlockBreakPriority.DISABLED ) {
    			
    			EventPriority ePriority = EventPriority.valueOf( eventPriority.name().toUpperCase() );           
    			
    			
    			OnBlockBreakEventTokenEnchantEventListenerMonitor normalListenerMonitor = 
    										new OnBlockBreakEventTokenEnchantEventListenerMonitor();

    			
    			SpigotPrison prison = SpigotPrison.getInstance();

    			PluginManager pm = Bukkit.getServer().getPluginManager();
    			
    			if ( eventPriority != BlockBreakPriority.MONITOR ) {
    				
    				if ( isBoolean( AutoFeatures.isAutoFeaturesEnabled )) {
    					
    					AutoManagerTokenEnchantEventListener autoManagerlListener = 
    							new AutoManagerTokenEnchantEventListener();
    					
    					pm.registerEvent(TEBlockExplodeEvent.class, autoManagerlListener, ePriority,
    							new EventExecutor() {
    						public void execute(Listener l, Event e) { 
    							((AutoManagerTokenEnchantEventListener)l)
    							.onTEBlockExplode((TEBlockExplodeEvent)e);
    						}
    					},
    							prison);
    					prison.getRegisteredBlockListeners().add( autoManagerlListener );
    				}

    				OnBlockBreakEventTokenEnchantEventListener normalListener = 
    						new OnBlockBreakEventTokenEnchantEventListener();
    				
    				pm.registerEvent(TEBlockExplodeEvent.class, normalListener, ePriority,
    						new EventExecutor() {
    					public void execute(Listener l, Event e) { 
    						((OnBlockBreakEventTokenEnchantEventListener)l)
    						.onTEBlockExplode((TEBlockExplodeEvent)e);
    					}
    				},
    						prison);
    				prison.getRegisteredBlockListeners().add( normalListener );
    			}
    			
    			pm.registerEvent(TEBlockExplodeEvent.class, normalListenerMonitor, EventPriority.MONITOR,
    					new EventExecutor() {
    				public void execute(Listener l, Event e) { 
	    					((OnBlockBreakEventTokenEnchantEventListenerMonitor)l)
	    						.onTEBlockExplode((TEBlockExplodeEvent)e);
    					}
	    			},
	    			prison);
    			prison.getRegisteredBlockListeners().add( normalListenerMonitor );
    		}
    		
    	}
    	catch ( ClassNotFoundException e ) {
    		// TokenEnchant is not loaded... so ignore.
    		Output.get().logInfo( "AutoManager: TokenEnchant is not loaded" );
    	}
    	catch ( Exception e ) {
    		Output.get().logInfo( "AutoManager: TokenEnchant failed to load. [%s]", e.getMessage() );
    	}
    }
    
	
    @Override
    public void unregisterListeners() {

    	super.unregisterListeners();
    	
    }
    
    
	@Override
	public void dumpEventListeners() {
		
		StringBuilder sb = new StringBuilder();
		
		dumpEventListeners( sb );
		
		if ( sb.length() > 0 ) {

			
			for ( String line : sb.toString().split( "\n" ) ) {
				
				Output.get().logInfo( line );
			}
		}
		
	}
	
	
    @Override
	public void dumpEventListeners( StringBuilder sb ) {
    	boolean isEventEnabled = isBoolean( AutoFeatures.isProcessTokensEnchantExplosiveEvents );
    	
    	if ( !isEventEnabled ) {
    		return;
    	}
		
		// Check to see if the class TEBlockExplodeEvent even exists:
		try {
			
			Class.forName( "com.vk2gpz.tokenenchant.event.TEBlockExplodeEvent", false, 
							this.getClass().getClassLoader() );
			

			ChatDisplay eventDisplay = Prison.get().getPlatform().dumpEventListenersChatDisplay( 
					"TEBlockExplodeEvent", 
					new SpigotHandlerList( TEBlockExplodeEvent.getHandlerList()) );

			if ( eventDisplay != null ) {
				sb.append( eventDisplay.toStringBuilder() );
			}
		}
		catch ( ClassNotFoundException e ) {
			// TokenEnchant is not loaded... so ignore.
		}
		catch ( Exception e ) {
			Output.get().logInfo( "AutoManager: TokenEnchant failed to load. [%s]", e.getMessage() );
		}
	}
    
	
}
