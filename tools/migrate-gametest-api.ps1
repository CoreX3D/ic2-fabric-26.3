$ErrorActionPreference = 'Stop'
$taskRoot = Split-Path -Parent $PSScriptRoot
$taskJavaRoot = Join-Path $taskRoot 'src\gametest\java'
$taskEnvironmentRoot = Join-Path $taskRoot 'src\gametest\resources\data\ic2\test_environment'
$taskEnvironmentIds = [System.Collections.Generic.HashSet[string]]::new()
$taskTestCount = 0

foreach ($taskFile in Get-ChildItem -LiteralPath $taskJavaRoot -Recurse -File -Filter '*.java') {
    $taskText = [System.IO.File]::ReadAllText($taskFile.FullName)
    $taskText = $taskText.Replace('import net.minecraft.gametest.framework.GameTest;', 'import net.fabricmc.fabric.api.gametest.v1.GameTest;')
    $taskText = $taskText.Replace('net.minecraft.world.entity.animal.Pig;', 'net.minecraft.world.entity.animal.pig.Pig;')
    $taskText = $taskText.Replace('net.minecraft.world.entity.animal.Sheep;', 'net.minecraft.world.entity.animal.sheep.Sheep;')
    $taskText = [regex]::Replace($taskText, '\bFarmBlock\b', 'FarmlandBlock')
    $taskText = [regex]::Replace($taskText, '\bClickType\b', 'ContainerInput')
    $taskText = [regex]::Replace($taskText, '\bEntityType\.([A-Z][A-Z_0-9]+)', 'net.minecraft.world.entity.EntityTypes.$1')
    $taskText = $taskText.Replace('Blocks.WHITE_WOOL', 'Blocks.WOOL.white()').Replace('Blocks.RED_WOOL', 'Blocks.WOOL.red()').Replace('Blocks.BLUE_STAINED_GLASS', 'Blocks.STAINED_GLASS.blue()')
    $taskText = $taskText.Replace('Items.WHITE_WOOL', 'Items.WOOL.white()').Replace('Items.BLUE_DYE', 'Items.DYE.blue()').Replace('Items.BLACK_DYE', 'Items.DYE.black()')
    $taskText = $taskText.Replace('MobEffects.MOVEMENT_SPEED', 'MobEffects.SPEED')
    $taskText = $taskText.Replace('ChunkPos.asLong(', 'ChunkPos.pack(').Replace('nearChunk.toLong()', 'nearChunk.pack()').Replace('farChunk.toLong()', 'farChunk.pack()')
    $taskText = $taskText.Replace('side.getNormal()', 'side.getUnitVec3i()')
    $taskText = $taskText.Replace('level.setDayTime(', 'Ic2GameTestUtil.setDayTime(level, ').Replace('level.setWeatherParameters(', 'Ic2GameTestUtil.setWeatherParameters(level, ')
    $taskText = [regex]::Replace($taskText, '\.assemble\((\w+), helper\.getLevel\(\)\.registryAccess\(\)\)', '.assemble($1)')
    $taskText = $taskText.Replace('helper.getLevel().getMaxBuildHeight()', '(helper.getLevel().getMaxY() + 1)')
    $taskText = $taskText.Replace('.readItemStack(crystal)', '.readItemStack(crystal, helper.getLevel().registryAccess())')
    $taskText = $taskText.Replace('level.getChunkAt(abs).setUnsaved(false)', 'level.getChunkAt(abs).tryMarkSaved()')
    $taskText = $taskText.Replace('player.moveTo(', 'player.snapTo(')
    $taskText = [regex]::Replace($taskText, '@GameTest\(([^)]*)\)', [System.Text.RegularExpressions.MatchEvaluator] {
        param($taskMatch)
        $taskArguments = $taskMatch.Groups[1].Value -replace '\btemplate\s*=', 'structure =' -replace '\btimeoutTicks\s*=', 'maxTicks ='
        $taskArguments = [regex]::Replace($taskArguments, '\bbatch\s*=\s*"([^"]+)"', [System.Text.RegularExpressions.MatchEvaluator] {
            param($taskBatchMatch)
            $taskEnvironmentName = ([regex]::Replace($taskBatchMatch.Groups[1].Value, '([a-z0-9])([A-Z])', '$1_$2')).ToLowerInvariant()
            [void]$taskEnvironmentIds.Add($taskEnvironmentName)
            return 'environment = "ic2:' + $taskEnvironmentName + '"'
        })
        # Vanilla 1.21.1's default was 100 ticks; Fabric 26.3 defaults to only 20.
        if ($taskArguments -notmatch '\bmaxTicks\s*=') {
            $taskArguments += ', maxTicks = 100'
        }
        return '@GameTest(' + $taskArguments + ')'
    })
    $taskText = [regex]::Replace($taskText, '(@GameTest\([^)]*\)\s*)public static void', '$1public void')
    # 26.3 requires the expected block entity class in this helper method.
    $taskText = [regex]::Replace($taskText, '\bhelper\.getBlockEntity\(([^(),]+)\)', 'helper.getBlockEntity($1, net.minecraft.world.level.block.entity.BlockEntity.class)')
    # The Optional-based tag accessors have explicit variants preserving the old defaults.
    $taskText = [regex]::Replace($taskText, '\.getBoolean\(("[^"]+")\)', '.getBooleanOr($1, false)')
    $taskText = [regex]::Replace($taskText, '\.getInt\(("[^"]+")\)', '.getIntOr($1, 0)')
    $taskText = [regex]::Replace($taskText, '\.getDouble\(("[^"]+")\)', '.getDoubleOr($1, 0.0)')
    $taskText = [regex]::Replace($taskText, '\.getCompound\(("[^"]+")\)', '.getCompoundOrEmpty($1)')
    $taskText = [regex]::Replace($taskText, '\.getList\(("[^"]+"),\s*10\)', '.getListOrEmpty($1)')
    $taskText = $taskText.Replace('.getInventory().items', '.getInventory().getNonEquipmentItems()')
    $taskText = $taskText.Replace('new ChunkPos(helper.absolutePos(LOADER_POS))', 'ChunkPos.containing(helper.absolutePos(LOADER_POS))')
    $taskText = [regex]::Replace($taskText, '\bmainChunk\.(x|z)\b(?!\()', 'mainChunk.$1()')
    if ($taskFile.Name -eq 'PersonalSafeGameTests.java') {
        $taskText = $taskText.Replace('owner.getId()', 'owner.id()').Replace('owner.getName()', 'owner.name()').Replace('safe.getOwner().getId()', 'safe.getOwner().id()')
    }
    if ($taskFile.Name -eq 'ScrapBoxGameTests.java') {
        $taskText = $taskText.Replace('InteractionResultHolder<ItemStack>', 'InteractionResult').Replace('InteractionResultHolder;', 'InteractionResult;').Replace('result.getResult().consumesAction()', 'result.consumesAction()')
    }
    if ($taskFile.Name -eq 'WindGameTests.java') {
        $taskText = $taskText.Replace('.use(helper.getLevel(), player, InteractionHand.MAIN_HAND).getResult()', '.use(helper.getLevel(), player, InteractionHand.MAIN_HAND)')
        $taskText = $taskText.Replace('stack.getDescriptionId()', 'stack.getItem().getDescriptionId()')
    }
    if ($taskFile.Name -eq 'ElectricWrenchGameTests.java') {
        $taskText = $taskText.Replace('helper.getBlockEntity(MACHINE_POS, net.minecraft.world.level.block.entity.BlockEntity.class)', 'helper.getBlockEntity(MACHINE_POS, TileEntityPersonalChest.class)')
    }
    if ($taskFile.Name -eq 'JetpackGameTests.java') {
        $taskText = [regex]::Replace($taskText, '(?<!\.getServer\(\))\.getRecipeManager\(\)', '.getServer().getRecipeManager()')
    }
    if ($taskFile.Name -eq 'QuantumSuitGameTests.java') {
        $taskText = $taskText.Replace('inventoryTick(helmet, helper.getLevel(), player, 0, false)', 'inventoryTick(helmet, helper.getLevel(), player, EquipmentSlot.HEAD)')
        $taskText = $taskText.Replace('inventoryTick(chest, helper.getLevel(), player, 0, false)', 'inventoryTick(chest, helper.getLevel(), player, EquipmentSlot.CHEST)')
        $taskText = $taskText.Replace('inventoryTick(leggings, helper.getLevel(), player, 0, false)', 'inventoryTick(leggings, helper.getLevel(), player, EquipmentSlot.LEGS)')
    }
    if ($taskFile.Name -in @('NanoSuitGameTests.java', 'NanoSaberGameTests.java')) {
        $taskText = $taskText.Replace('(attribute, modifier) ->', '(attribute, modifier, display) ->')
    }
    $taskText = [regex]::Replace($taskText, '\.loadWithComponents\((\w+), (helper\.getLevel\(\)|level)\.registryAccess\(\)\)', '.loadWithComponents(net.minecraft.world.level.storage.TagValueInput.create(net.minecraft.util.ProblemReporter.DISCARDING, $2.registryAccess(), $1))')
    $taskTestCount += [regex]::Matches($taskText, '@GameTest\(').Count
    [System.IO.File]::WriteAllText($taskFile.FullName, $taskText, [System.Text.UTF8Encoding]::new($false))
}

New-Item -ItemType Directory -Path $taskEnvironmentRoot -Force | Out-Null
foreach ($taskEnvironmentId in $taskEnvironmentIds) {
    # Distinct environments preserve the upstream batch separation for global-state tests.
    [System.IO.File]::WriteAllText((Join-Path $taskEnvironmentRoot ($taskEnvironmentId + '.json')), "{`n  `"type`": `"minecraft:all_of`",`n  `"definitions`": []`n}`n", [System.Text.UTF8Encoding]::new($false))
}
"MIGRATED_GAME_TEST_METHODS=$taskTestCount"
"CUSTOM_TEST_ENVIRONMENTS=$($taskEnvironmentIds.Count)"
