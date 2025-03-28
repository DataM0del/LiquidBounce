<script lang="ts">
    import {onMount, tick} from "svelte";
    import type {Module} from "../../../integration/types";
    import {getModule, getModules} from "../../../integration/rest";
    import {listen} from "../../../integration/ws";
    import {getTextWidth} from "../../../integration/text_measurement";
    import {flip} from "svelte/animate";
    import {fly} from "svelte/transition";
    import {convertToSpacedString, spaceSeperatedNames} from "../../../theme/theme_config";

    let enabledModules: Module[] = [];

    function sortByWidth(modules: Module[]): Module[] {
        return modules.map(m => {
                let formattedName = $spaceSeperatedNames ? convertToSpacedString(m.name) : m.name;
                let fullName = m.tag == null ? formattedName : formattedName + " " + m.tag;

                return {
                    ...m,
                    width: getTextWidth(fullName, "500 14px Inter")
                };
            }
        ).toSorted((a, b) => b.width - a.width).map(a => { // remove width property because yes
            a.width = undefined as unknown as number;
            return a;
        });
    }

    async function updateEnabledModules() {
        const modules = await getModules();
        const visibleModules = modules.filter(m => m.enabled && !m.hidden);

        enabledModules = sortByWidth(visibleModules);
        await tick();
    }
    async function updateModule(modName: string) {
        const m = await getModule(modName);
        if (!m.enabled || m.hidden) {
            const index = enabledModules.indexOf(m);
            if (index > -1) {
                enabledModules.splice(index, 1);
            }
        }


        const index = enabledModules.indexOf(m);
        if (index !== -1) {
            enabledModules.splice(index, 1);
            enabledModules[index] = m;
            // this probably gets rid of 50% of the performance boost...
            enabledModules = sortByWidth(enabledModules);
        }
        await tick();
    }

    spaceSeperatedNames.subscribe(async () => {
        await updateEnabledModules();
    });

    onMount(async () => {
        await updateEnabledModules();
    });

    listen("moduleToggle", async () => {
        await updateEnabledModules();
    });

    listen("refreshArrayList", async () => {
        await updateEnabledModules();
    });
    listen("refreshModuleInArrayList", async (modName: string) => {
        await updateModule(modName);
    });
</script>

<div class="arraylist">
    {#each enabledModules as {name, tag} (name)}
        <div class="module" animate:flip={{ duration: 200 }} transition:fly={{ x: 50, duration: 200 }}>
            {$spaceSeperatedNames ? convertToSpacedString(name) : name}
            {#if tag}
                <span class="tag"> {tag}</span>
            {/if}
        </div>
    {/each}
</div>

<style lang="scss">
  @use "../../../colors.scss" as *;

  .module {
    background-color: rgba($arraylist-base-color, 0.68);
    color: $arraylist-text-color;
    font-size: 14px;
    border-radius: 4px 0 0 4px;
    padding: 5px 8px;
    border-left: solid 4px $accent-color;
    width: max-content;
    font-weight: 500;
    margin-left: auto;
  }

  .tag {
    color: $arraylist-tag-color;
  }
</style>
