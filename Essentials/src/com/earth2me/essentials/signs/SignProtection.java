package com.earth2me.essentials.signs;

import com.earth2me.essentials.ChargeException;
import com.earth2me.essentials.IEssentials;
import com.earth2me.essentials.Trade;
import com.earth2me.essentials.User;
import com.earth2me.essentials.Util;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Sign;
import org.bukkit.craftbukkit.block.CraftSign;
import org.bukkit.inventory.ItemStack;


public class SignProtection extends EssentialsSign
{
	private final transient Set<Material> protectedBlocks = EnumSet.noneOf(Material.class);

	public SignProtection()
	{
		super("Protection");
		protectedBlocks.add(Material.CHEST);
		protectedBlocks.add(Material.BURNING_FURNACE);
		protectedBlocks.add(Material.FURNACE);
		protectedBlocks.add(Material.DISPENSER);
	}

	@Override
	protected boolean onSignCreate(final ISign sign, final User player, final String username, final IEssentials ess) throws SignException, ChargeException
	{
		sign.setLine(3, "§4" + username);
		if (hasAdjacentBlock(sign.getBlock()) && isBlockProtected(sign.getBlock(), player, username) != SignProtectionState.NOT_ALLOWED)
		{
			sign.setLine(3, "§1" + username);
		}
		return true;
	}

	@Override
	protected boolean onSignBreak(final ISign sign, final User player, final String username, final IEssentials ess) throws SignException
	{
		final SignProtectionState state = checkProtectionSign(sign, player, username);
		return state == SignProtectionState.OWNER;
	}

	public boolean hasAdjacentBlock(final Block block, final Block... ignoredBlocks)
	{
		final Block[] faces = getAdjacentBlocks(block);
		for (Block b : faces)
		{
			for (Block ignoredBlock : ignoredBlocks)
			{
				if (b.getLocation().equals(ignoredBlock.getLocation()))
				{
					continue;
				}
			}
			if (protectedBlocks.contains(b.getType()))
			{
				return true;
			}
		}
		return false;
	}

	private void checkIfSignsAreBroken(final Block block, final User player, final String username, final IEssentials ess)
	{
		final Map<Location, SignProtectionState> signs = getConnectedSigns(block, player, username);
		for (Map.Entry<Location, SignProtectionState> entry : signs.entrySet())
		{
			if (entry.getValue() != SignProtectionState.NOSIGN)
			{
				final Block sign = entry.getKey().getBlock();
				if (!hasAdjacentBlock(sign, block))
				{
					block.setType(Material.AIR);
					final Trade trade = new Trade(new ItemStack(Material.SIGN, 1), ess);
					trade.pay(player);
				}
			}
		}
	}

	private Map<Location, SignProtectionState> getConnectedSigns(final Block block, final User user, final String username)
	{
		final Map<Location, SignProtectionState> signs = new HashMap<Location, SignProtectionState>();
		getConnectedSigns(block, signs, user, username, 2);
		return signs;
	}

	private void getConnectedSigns(final Block block, final Map<Location, SignProtectionState> signs, final User user, final String username, final int depth)
	{
		final Block[] faces = getAdjacentBlocks(block);
		for (Block b : faces)
		{
			final Location loc = b.getLocation();
			if (signs.containsKey(loc))
			{
				continue;
			}
			final SignProtectionState check = checkProtectionSign(b, user, username);
			signs.put(loc, check);

			if (protectedBlocks.contains(b.getType()) && depth > 0)
			{
				getConnectedSigns(b, signs, user, username, depth - 1);
			}
		}
	}


	public enum SignProtectionState
	{
		NOT_ALLOWED, ALLOWED, NOSIGN, OWNER
	}

	private SignProtectionState checkProtectionSign(final Block block, final User user, final String username)
	{
		if (block.getType() == Material.SIGN_POST || block.getType() == Material.WALL_SIGN)
		{
			final BlockSign sign = new BlockSign(block);
			if (sign.getLine(0).equalsIgnoreCase(this.getSuccessName()))
			{
				return checkProtectionSign(sign, user, username);
			}
		}
		return SignProtectionState.NOSIGN;
	}

	private SignProtectionState checkProtectionSign(final ISign sign, final User user, final String username)
	{
		if (user == null || username == null)
		{
			return SignProtectionState.NOT_ALLOWED;
		}
		if (user.isAuthorized("essentials.signs.protection.override"))
		{
			return SignProtectionState.OWNER;
		}
		for (int i = 1; i <= 2; i++)
		{
			final String line = sign.getLine(i);
			if (line.startsWith("(") && line.endsWith(")") && user.inGroup(line.substring(1, line.length() - 1)))
			{
				return SignProtectionState.ALLOWED;
			}
			else if (line.equalsIgnoreCase(username))
			{
				return SignProtectionState.ALLOWED;
			}
		}
		if (sign.getLine(3).equalsIgnoreCase(username))
		{
			return SignProtectionState.OWNER;
		}
		return SignProtectionState.NOT_ALLOWED;
	}

	private Block[] getAdjacentBlocks(final Block block)
	{
		return new Block[]
				{
					block.getFace(BlockFace.NORTH),
					block.getFace(BlockFace.SOUTH),
					block.getFace(BlockFace.EAST),
					block.getFace(BlockFace.WEST),
					block.getFace(BlockFace.DOWN),
					block.getFace(BlockFace.UP)
				};
	}

	public SignProtectionState isBlockProtected(final Block block, final User user, final String username)
	{
		final Map<Location, SignProtectionState> signs = getConnectedSigns(block, user, username);
		SignProtectionState retstate = SignProtectionState.NOSIGN;
		for (SignProtectionState state : signs.values())
		{
			if (state == SignProtectionState.OWNER || state == SignProtectionState.ALLOWED)
			{
				return state;
			}
			if (state == SignProtectionState.NOT_ALLOWED)
			{
				retstate = state;
			}
		}
		return retstate;
	}

	public boolean isBlockProtected(final Block block)
	{
		final Block[] faces = getAdjacentBlocks(block);
		for (Block b : faces)
		{
			if (b.getType() == Material.SIGN_POST || b.getType() == Material.WALL_SIGN)
			{
				final Sign sign = new CraftSign(b);
				if (sign.getLine(0).equalsIgnoreCase("§1[Protection]"))
				{
					return true;
				}
			}
			if (protectedBlocks.contains(b.getType()))
			{
				final Block[] faceChest = getAdjacentBlocks(b);

				for (Block a : faceChest)
				{
					if (a.getType() == Material.SIGN_POST || a.getType() == Material.WALL_SIGN)
					{
						final Sign sign = new CraftSign(a);
						if (sign.getLine(0).equalsIgnoreCase("§1[Protection]"))
						{
							return true;
						}
					}
				}
			}
		}
		return false;
	}

	@Override
	public Set<Material> getBlocks()
	{
		return protectedBlocks;
	}

	@Override
	protected boolean onBlockBreak(final Block block, final User player, final String username, final IEssentials ess) throws SignException
	{
		final SignProtectionState state = isBlockProtected(block, player, username);

		if (state == SignProtectionState.OWNER || state == SignProtectionState.NOSIGN)
		{
			checkIfSignsAreBroken(block, player, username, ess);
			return true;
		}

		if ((state == SignProtectionState.ALLOWED || state == SignProtectionState.NOT_ALLOWED)
			&& player.isAuthorized("essentials.signs.protection.override"))
		{
			checkIfSignsAreBroken(block, player, username, ess);
			return true;
		}


		player.sendMessage(Util.format("noDestroyPermission", block.getType().toString().toLowerCase()));
		return false;
	}

	@Override
	public boolean onBlockExplode(final Block block, final IEssentials ess)
	{
		final SignProtectionState state = isBlockProtected(block, null, null);

		return state == SignProtectionState.NOSIGN;
	}

	@Override
	public boolean onBlockBurn(final Block block, final IEssentials ess)
	{
		final SignProtectionState state = isBlockProtected(block, null, null);

		return state == SignProtectionState.NOSIGN;
	}
}
