package me.twentyonez.guardianchest.common;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import me.twentyonez.guardianchest.GuardianChest;
import me.twentyonez.guardianchest.block.GCBlocks;
import me.twentyonez.guardianchest.compat.GCBattlegear;
import me.twentyonez.guardianchest.compat.GCBaubles;
import me.twentyonez.guardianchest.compat.GCCampingMod;
import me.twentyonez.guardianchest.compat.GCGalacticraft;
import me.twentyonez.guardianchest.compat.GCRpgInventory;
import me.twentyonez.guardianchest.compat.GCTinkersConstruct;
import me.twentyonez.guardianchest.compat.GCTravellersGear;
import me.twentyonez.guardianchest.compat.GCTwilightForest;
import me.twentyonez.guardianchest.compat.GCminecraft;
import me.twentyonez.guardianchest.compat.GCsoulBinding;
import me.twentyonez.guardianchest.tile_entity.TileEntityGCChest;
import me.twentyonez.guardianchest.util.ConfigHelper;

import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.ChunkCoordinates;
import net.minecraft.util.MathHelper;

import net.minecraft.world.World;
import net.minecraftforge.event.entity.EntityJoinWorldEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.PlayerEvent.ItemCraftedEvent;
import cpw.mods.fml.common.registry.LanguageRegistry;

/**
 * GuardianChest mod
 *
 * @author TwentyOneZ
 * @license Lesser GNU Public License v3 (http://www.gnu.org/licenses/lgpl.html)
 * 
 * Based on NightKosh's GraveStone mod, Dr.Cyano's Lootable Corpses mod and Tyler15555's Death Chest mod.
 */
public class GCEventHandler {

	public GCEventHandler() {
		
	}
	
	Map<UUID, ArrayList<ItemStackTypeSlot>> player_itemStackTypeSlots = new HashMap<>(50);
	Map<UUID, Integer> player_levelSoulBoundInventory = new HashMap<>(50);

	private ArrayList<ItemStackTypeSlot> getItemStackTypeSlots(final EntityPlayer entityPlayer) {
		return player_itemStackTypeSlots.computeIfAbsent(entityPlayer.getPersistentID(), k -> new ArrayList<>(64));
	}
	
	private int getSoulBoundInventoryLevel(final EntityPlayer entityPlayer) {
		return player_levelSoulBoundInventory.computeIfAbsent(entityPlayer.getPersistentID(), k -> 0);
	}
	
	private void setSoulBoundInventoryLevel(final EntityPlayer entityPlayer, final Integer levelSoulBoundInventory) {
		player_levelSoulBoundInventory.put(entityPlayer.getPersistentID(), levelSoulBoundInventory);
	}
	
	@SubscribeEvent(priority=EventPriority.LOWEST)
	public void playerCraftsBoundMapTier0(ItemCraftedEvent event) {
		EntityPlayer player = event.player;
		if (event.crafting.getItem() == GuardianChest.boundMapTier0) {
			GuardianChest.guardianTier1.setContainerItem(GuardianChest.guardianTier1);
			World world = player.worldObj;
			if (world.isRemote) {
				String warning = LanguageRegistry.instance().getStringLocalization("desc.boundMapTier0.Crafted")
				                 .replace("%1", LanguageRegistry.instance().getStringLocalization("tile.guardianChest.name"));
	    		player.addChatComponentMessage(new ChatComponentText(warning));
			}
    		
		} else {
			GuardianChest.guardianTier1.setContainerItem(null);
		}
		if (event.crafting.getItem() == GuardianChest.boundMapTier1) {
			GuardianChest.guardianTier2.setContainerItem(GuardianChest.guardianTier1);
		} else {
			GuardianChest.guardianTier2.setContainerItem(null);
		}
	}
	
	@SubscribeEvent(priority=EventPriority.HIGH)
	public void onPlayerRespawn(EntityJoinWorldEvent event) {
		if (event.entity instanceof EntityPlayer) {
			final EntityPlayer entityPlayer = (EntityPlayer) event.entity;
			GCsoulBinding.startCounting(entityPlayer);
			
			if (entityPlayer.isEntityAlive()) {
				final ArrayList<ItemStackTypeSlot> itemStackTypeSlots = getItemStackTypeSlots(entityPlayer);
				final int levelSoulBoundInventory = getSoulBoundInventoryLevel(entityPlayer);
				if (GuardianChest.DEBUG) {
					GuardianChest.logger.info(String.format("isAlive %s (%d) level %d",
					                                        itemStackTypeSlots, itemStackTypeSlots.size(), levelSoulBoundInventory));
				}
	        	GCsoulBinding.getSoulBoundItemsBack(itemStackTypeSlots, entityPlayer, levelSoulBoundInventory);
				itemStackTypeSlots.clear();
				setSoulBoundInventoryLevel(entityPlayer, 0);
			}
		}
	}
	
	@SubscribeEvent(priority=EventPriority.HIGH) 
	public void onPlayerDeath(LivingDeathEvent event) {
		if (event.entityLiving instanceof EntityPlayer) {
			
			final EntityPlayer entityPlayer = (EntityPlayer) event.entityLiving;
			
			if (event.isCanceled()) {
				GuardianChest.logger.info(String.format("Death cancelled, skipping guardian chest creation for %s", entityPlayer));
				return;
			}
			
			GCsoulBinding.startCounting(entityPlayer);
			
			// Check if config file defines the Guardian Stone as a requirement for the chest to work 
			int saveItems = 0;
			if(!ConfigHelper.requireGuardianIdol) {
				saveItems = -1;
			} else {
				if (entityPlayer.inventory.hasItem(GuardianChest.guardianTier2)) {
					entityPlayer.inventory.consumeInventoryItem(GuardianChest.guardianTier2);
					saveItems = 2;
				} else if (entityPlayer.inventory.hasItem(GuardianChest.guardianTier1)) {
					entityPlayer.inventory.consumeInventoryItem(GuardianChest.guardianTier1);
					saveItems = 1;
				}
			}

			// Check for Twilight Forest Charms of Keeping
			int levelSoulBoundInventory = 0;
			if (GCTwilightForest.isInstalled()) {
				if (entityPlayer.inventory.hasItem(twilightforest.item.TFItems.charmOfKeeping3)) {
					entityPlayer.inventory.consumeInventoryItem(twilightforest.item.TFItems.charmOfKeeping3);
					levelSoulBoundInventory = 3;
				} else if (entityPlayer.inventory.hasItem(twilightforest.item.TFItems.charmOfKeeping2)) {
					entityPlayer.inventory.consumeInventoryItem(twilightforest.item.TFItems.charmOfKeeping2);
					levelSoulBoundInventory = 2;
				} else if (entityPlayer.inventory.hasItem(twilightforest.item.TFItems.charmOfKeeping1)) {
					entityPlayer.inventory.consumeInventoryItem(twilightforest.item.TFItems.charmOfKeeping1);
					levelSoulBoundInventory = 1;
				}
			}
			setSoulBoundInventoryLevel(entityPlayer, levelSoulBoundInventory);
			
			final ArrayList<ItemStackTypeSlot> itemStackTypeSlots = getItemStackTypeSlots(entityPlayer);
			
			// Get Vanilla inventory
        	GCminecraft.addItems(itemStackTypeSlots, entityPlayer, saveItems, levelSoulBoundInventory);
			
			// Get Battlegear inventory
            if (GCBattlegear.isInstalled()) {
            	GCBattlegear.addItems(itemStackTypeSlots, entityPlayer, saveItems, levelSoulBoundInventory);
            }
			
			// Get Baubles inventory
            if (GCBaubles.isInstalled()) {
            	GCBaubles.addItems(itemStackTypeSlots, entityPlayer, saveItems, levelSoulBoundInventory);
            }

			// Get Galacticraft inventory
            if (GCGalacticraft.isInstalled()) {
            	GCGalacticraft.addItems(itemStackTypeSlots, entityPlayer, saveItems, levelSoulBoundInventory);
            }

			// Get RpgInventory inventory
            if (GCRpgInventory.isInstalled()) {
            	GCRpgInventory.addItems(itemStackTypeSlots, entityPlayer, saveItems, levelSoulBoundInventory);
            }

			// Get The Camping Mod inventory
            if (GCCampingMod.isInstalled()) {
            	GCCampingMod.addItems(itemStackTypeSlots, entityPlayer, saveItems, levelSoulBoundInventory);
            }
            
            // Get the TravellersGear inventory
			if (GCTravellersGear.isInstalled()) {
				GCTravellersGear.addItems(itemStackTypeSlots, entityPlayer, saveItems, levelSoulBoundInventory);
			}
			
			// Get Tinker's Construct inventory
            if (GCTinkersConstruct.isInstalled()) {
            	GCTinkersConstruct.addItems(itemStackTypeSlots, entityPlayer, saveItems, levelSoulBoundInventory);
            }
			
            // Get the player death coords. If it's out of the world, get last slept location. If player did
			// not sleep yet, get world spawn coords.
			if (ConfigHelper.enableDebugLogs) {
				GuardianChest.logger.info(String.format("saveItems %d", saveItems));
			}
			if (saveItems != 0) {
				int posX1 = MathHelper.floor_double(entityPlayer.posX);
				int posY1 = MathHelper.floor_double(entityPlayer.posY);
				int posZ1 = MathHelper.floor_double(entityPlayer.posZ);
				
				World world = entityPlayer.worldObj;
				if (ConfigHelper.enableDebugLogs) {
					GuardianChest.logger.info(String.format("position DIM%d @ (%d %d %d)", world.provider.dimensionId, posX1, posY1, posZ1));
				}
				
				if ((posY1 <= 0) || (saveItems == 2) || ((saveItems == -1) && (ConfigHelper.defaultsToTier2))) {
					ChunkCoordinates bed = entityPlayer.getBedLocation(entityPlayer.dimension);
					if (bed == null) {
						world = MinecraftServer.getServer().worldServerForDimension(0);
						bed = entityPlayer.getBedLocation(0);
					}
					
					if (bed != null) {
						posY1 = bed.posY;
						posX1 = bed.posX+1;
						posZ1 = bed.posZ+1;
					} else {
						posX1 = world.getSpawnPoint().posX+1;
						posY1 = world.getSpawnPoint().posY;
						posZ1 = world.getSpawnPoint().posZ;
					}
				}
				
				// Look for a free spot
				final int radius = ConfigHelper.maxRadiusToSearchForAFreeSpot;
				if (ConfigHelper.enableDebugLogs) {
					GuardianChest.logger.info(String.format("maxRadiusToSearchForAFreeSpot %d", radius));
				}
				if (!isFreeSpot(world, posX1, posY1, posZ1, true)) {
					if (ConfigHelper.enableDebugLogs) {
						GuardianChest.logger.info("Initial position is bad, searching...");
					}
					int newX = posX1;
					int newY = posY1;
					int newZ = posZ1;
					int distanceClosest = Integer.MAX_VALUE;
					for (int x = -radius; x <= radius; x++) {
						for (int z = -radius; z <= radius; z++) {
							for (int y = - 2 * radius; y <= 2 * radius; y++) {
								if (isFreeSpot(world, posX1 + x, posY1 + y, posZ1 + z, true)) {
									if (ConfigHelper.enableDebugLogs) {
										GuardianChest.logger.info(String.format("found free spot at (%d %d %d)", posX1 + x, posY1 + y, posZ1 + z));
									}
									final int distanceCurrent = (x * x) + (y * y) + (z * z);
									if (distanceCurrent < distanceClosest) {
										if (ConfigHelper.enableDebugLogs) {
											GuardianChest.logger.info(String.format("new free spot is closer: %d -> %d", distanceClosest, distanceCurrent));
										}
										distanceClosest = distanceCurrent;
										newX = posX1 + x;
										newY = posY1 + y;
										newZ = posZ1 + z;
									}
								}
							}
						}
					}
					if (ConfigHelper.enableDebugLogs) {
						GuardianChest.logger.info(String.format("Search closest distance is %d at (%d %d %d)", distanceClosest, newX, newY, newZ));
					}
					if (distanceClosest != Integer.MAX_VALUE) {// (free spot found)
						if (ConfigHelper.enableDebugLogs) {
							GuardianChest.logger.info("Search was a success!");
						}
						posX1 = newX;
						posY1 = newY;
						posZ1 = newZ;
					} else {// (no free spot, use top solid block if possible)
						newY = world.getTopSolidOrLiquidBlock(posX1, posZ1);
						if (ConfigHelper.enableDebugLogs) {
							GuardianChest.logger.info(String.format("Search failed, checking top block at (%d %d %d)", posX1, newY, posZ1));
						}
						if ( isFreeSpot(world, posX1, newY, posZ1, false) ) {
							if (ConfigHelper.enableDebugLogs) {
								GuardianChest.logger.info("Search failed, but top block is good to go");
							}
							posY1 = newY;
						} else if ( posY1 <= 2
						         || posY1 >= 255 ) {
							// probably in empty space, but current position is bad, so we defaults to 128 
							if (ConfigHelper.enableDebugLogs) {
								GuardianChest.logger.info("Search failed, current position is bad, using defaults of 128");
							}
							posY1 = 128;
						}
					}
				}
				
				// Create chest
				GuardianChest.logger.info(String.format("Creating GuardianChest at DIM %d (%d %d %d)",
				                                         world.provider.dimensionId, posX1, posY1, posZ1));
				world.setBlock(posX1, posY1, posZ1, GCBlocks.GCChest, 0, 2);
				final TileEntityGCChest tileEntityGCChest = (TileEntityGCChest) world.getTileEntity(posX1, posY1, posZ1);
				if (tileEntityGCChest == null) {
					GuardianChest.logger.error(String.format("GuardianChest without tile entity at DIM %d (%d %d %d)",
					                                         world.provider.dimensionId, posX1, posY1, posZ1));
				}
				
				// Inform related player of its existence
				if ((!world.isRemote) && (ConfigHelper.informCoords)) {
		    		final String message = LanguageRegistry.instance().getStringLocalization("desc.SpawnLocation.Warning")
				                           .replace("%1", LanguageRegistry.instance().getStringLocalization("tile.guardianChest.name"))
				                           .replace("%2", entityPlayer.getDisplayName())
				                           .replace("%3", String.format("DIM %d (%d %d %d)", world.provider.dimensionId, posX1, posY1, posZ1));
					
					entityPlayer.addChatComponentMessage(new ChatComponentText(message));
		    	}
				
				// Dump player inventory into chest 
				int indexChestSlot = 0;
				
				// Return a BoundMapTier0 to the chest if the chest was a Tier2.
				if (saveItems == 2 && ConfigHelper.returnChestToInventory && tileEntityGCChest != null) {
					tileEntityGCChest.setInventorySlotContents(indexChestSlot, new ItemStack(GuardianChest.boundMapTier0));
					indexChestSlot++;
				}
				// Add an ItemGuardianTier0 to chest.
				if (saveItems != -1 && ConfigHelper.returnChestToInventory && tileEntityGCChest != null) {
					if (ConfigHelper.levelCostGuardianTier1 != 0) {
						tileEntityGCChest.setInventorySlotContents(indexChestSlot, new ItemStack(GuardianChest.guardianTier0));
					} else {
						tileEntityGCChest.setInventorySlotContents(indexChestSlot, new ItemStack(GuardianChest.guardianTier1));
					}
					indexChestSlot++;
				}
				
				
				
				if (ConfigHelper.enableDebugLogs) {
					GuardianChest.logger.info(String.format("Before dump into chest %s (%d) level %d",
					                                        itemStackTypeSlots, itemStackTypeSlots.size(), levelSoulBoundInventory));
				}
				
				// Dump collected inventory into chest				
				for (ItemStackTypeSlot itemStackTypeSlot : itemStackTypeSlots) {
					if (!GCsoulBinding.keepItem(itemStackTypeSlot, entityPlayer, levelSoulBoundInventory)) {
						if ( tileEntityGCChest == null
						  || indexChestSlot > tileEntityGCChest.getSizeInventory() - 1 ) {// Chest is missing or full 
							// Returning item to player's inventory, so it drops
							int countdown = 100;
							while ( countdown > 0
							     && itemStackTypeSlot.itemStack.stackSize > 0
							     && !entityPlayer.inventory.addItemStackToInventory(itemStackTypeSlot.itemStack) ) {
								entityPlayer.dropOneItem(true);
								countdown--;
							}
							if (countdown == 0) {
								GuardianChest.logger.error(String.format("Unable to return %s of type %s in slot %d of chest %s for player %s",
								                                         itemStackTypeSlot.itemStack,
								                                         itemStackTypeSlot.type.getUnlocalizedName(),
								                                         itemStackTypeSlot.indexSlot,
								                                         tileEntityGCChest == null ? "-null-" : tileEntityGCChest.toString(),
								                                         entityPlayer));
							}
							
						} else {// Filling chest
							
							if (ConfigHelper.enableDebugLogs) {
								GuardianChest.logger.info(String.format("Adding to chest[%d] of %s",
								                                        indexChestSlot, itemStackTypeSlot.itemStack));
							}
							tileEntityGCChest.setInventorySlotContents(indexChestSlot, itemStackTypeSlot.itemStack);
							indexChestSlot++;
						}
					}
				}
				if (!world.isRemote && tileEntityGCChest != null) {
	                tileEntityGCChest.registerOwner(entityPlayer, world, posX1, posY1, posZ1);
		        }
			}
		}
	}
	
    private static boolean isFreeSpot(final World world, final int posX, final int posY, final int posZ, final boolean isRequiringSolidBlock) {
		if (posY < 1 || posY > 255) {// ignore bottom (we might need a solid block below) and top of world (some mods are using it)
			return false;
		}
		final Block block = world.getBlock(posX, posY, posZ);
        return ( !isRequiringSolidBlock
              || world.getBlock(posX, posY - 1, posZ).getMaterial().isSolid() )
            && ( !block.getMaterial().isLiquid() )
            && ( block.isAir(world, posX, posY, posZ)
              || block.isReplaceable(world, posX, posY, posZ) );
    }

}