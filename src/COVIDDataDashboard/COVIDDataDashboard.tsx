import React, { FunctionComponent } from "react"
import { StyleSheet, View } from "react-native"

import StateData from "./StateData"
import { useConfigurationContext } from "../ConfigurationContext"

import { Affordances, Spacing } from "../styles"

const COVIDDataDashboard: FunctionComponent = () => {
  const { stateAbbreviation } = useConfigurationContext()

  if (stateAbbreviation === null) {
    return null
  }

  return (
    <View style={style.dataContainer}>
      <StateData stateAbbreviation={stateAbbreviation} />
    </View>
  )
}

const style = StyleSheet.create({
  dataContainer: {
    ...Affordances.floatingContainer,
    marginTop: Spacing.large,
    marginHorizontal: Spacing.small,
    padding: 0,
  },
})

export default COVIDDataDashboard